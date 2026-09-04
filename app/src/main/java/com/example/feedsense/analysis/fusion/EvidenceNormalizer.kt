package com.example.feedsense.analysis.fusion

// --------------------------------
// EVIDENCE NORMALIZER (Milestone 8B-1)
// --------------------------------
//
// 1. NORMALIZATION : collapses a provider's per-record confidence
//    and strength into a single normalized score in [0,1],
//    scaled by the centralized family reliability and the family
//    weight from FusionConfig.
//
// 2. INFORMATION QUALITY : analyses whether the frame set carried
//    enough informative text to be analyzed at all.
//
// 3. DUPLICATE-EVIDENCE CONTROL : identical evidence (same family
//    + same value summary) is significant once, not once per
//    frame. 8 identical OCR frames are one confirmation, not
//    eight (8B-1 section 19/44). This prevents confidence
//    inflation from redundant frames.
//
// 4. CONFLICT DETECTION : surfaces when informative records
//    disagree on a candidate (8B-1 section 26).

data class NormalizedEvidence(
    val record: EvidenceRecord,
    val normalizedScore: Double,
    val sourceFamilyWeight: Double,
    val deduplicated: Boolean
)

data class InformationQuality(
    val informativeTextFrames: Int,
    val totalFrames: Int,
    val hasInformativeText: Boolean,
    val note: String
) {
    val sufficient: Boolean get() = hasInformativeText
}

class EvidenceNormalizer(
    private val config: FusionConfig
) {

    fun quality(context: EvidenceContext): InformationQuality {
        val informative = context.frames.count {
            it.hasOcr &&
                it.ocrText!!.trim().length >= config.minInformativeTextChars
        }
        val note = when {
            context.frames.isEmpty() -> "NO_FRAMES"
            informative == 0 -> "INSUFFICIENT_INFORMATIVE_TEXT"
            informative < context.frames.size -> "PARTIAL_INFORMATIVE_TEXT"
            else -> "ALL_FRAMES_INFORMATIVE"
        }
        return InformationQuality(
            informativeTextFrames = informative,
            totalFrames = context.frames.size,
            hasInformativeText = informative > 0,
            note = note
        )
    }

    fun familyWeight(family: EvidenceFamily): Double {
        return when (family) {
            EvidenceFamily.TEXT -> config.textWeight
            EvidenceFamily.PLATFORM -> config.platformWeight
            EvidenceFamily.VISUAL -> config.visualWeight
            EvidenceFamily.TEMPORAL -> config.temporalWeight
            EvidenceFamily.INTERACTION -> config.interactionWeight
            EvidenceFamily.CLASSIFIER -> config.textWeight
        }
    }

    /**
     * Deduplicates records: a record whose (family,type,valueSummary)
     * was already seen is marked as a duplicate and contributes
     * nothing to score accumulation (it may still be referenced by
     * the trace / frame bookkeeping).
     */
    fun deduplicate(
        records: List<EvidenceRecord>
    ): List<NormalizedEvidence> {
        val seen = HashSet<String>()
        val out = mutableListOf<NormalizedEvidence>()

        // A genuinely informative unique record is NOT a duplicate,
        // even if it came from several frames; only the same
        // valueSummary within the same family collapses. Single
        // virtual "unavailable" records also survive once.
        for (r in records) {
            val key = r.family.name + "|" + r.type + "|" + r.valueSummary
            val dup = !seen.add(key)
            out += NormalizedEvidence(
                record = r,
                normalizedScore = normalizedScoreOf(r),
                sourceFamilyWeight = familyWeight(r.family),
                deduplicated = dup
            )
        }
        return out
    }

    /**
     * Combines raw confidence (if any) and integrity strength into
     * a single [0,1] score, then scales by centralized reliability.
     * This is the raw, pre-weight contribution of one record.
     */
    private fun normalizedScoreOf(r: EvidenceRecord): Double {
        val strength = r.strength.value
        val conf = r.confidence?.coerceIn(0.0, 1.0) ?: strength
        // Score = confidence blended with integrity strength,
        // capped at 1, then scaled by provider reliability.
        return (0.4 * strength + 0.6 * conf) * r.reliability
    }

    /**
     * Surfaces conflicting informative records: two informative
     * records of different families whose content summaries do not
     * agree and which each carry non-trivial direct support.
     * Returns the count of conflict pairs.
     */
    fun conflictCount(
        normalized: List<NormalizedEvidence>
    ): Int {
        val informative = normalized.filter {
            !it.deduplicated && !it.record.isVirtual &&
                it.record.strength != EvidenceStrength.NONE
        }
        // Simple, deterministic conflict proxy: two informative
        // records from different non-context families that disagree.
        var conflicts = 0
        for (i in informative.indices) {
            for (j in i + 1 until informative.size) {
                val a = informative[i].record
                val b = informative[j].record
                if (a.family == b.family) continue
                val contextFamily =
                    a.family == EvidenceFamily.PLATFORM ||
                        b.family == EvidenceFamily.PLATFORM
                if (contextFamily) continue
                if (summaryAgrees(a, b)) continue
                conflicts++
            }
        }
        return conflicts
    }

    private fun summaryAgrees(
        a: EvidenceRecord,
        b: EvidenceRecord
    ): Boolean {
        val sa = a.valueSummary.trim().lowercase()
        val sb = b.valueSummary.trim().lowercase()
        if (sa.isEmpty() || sb.isEmpty()) return false
        // Treat near-identical summaries as agreeing; otherwise
        // disagree only if they share no tokens at all.
        val ta = sa.split("\\s+".toRegex())
        val tb = sb.split("\\s+".toRegex())
        val overlap = ta.any { it in tb }
        return overlap
    }
}
