package com.example.feedsense.analysis.fusion

import com.example.feedsense.analysis.CategoryCatalog

// --------------------------------
// FUSION ENGINE (Milestone 8B-1)
// --------------------------------
//
// Layer D (fusion) + Layer E (uncertainty) + Layer F (decision).
//
// Pure, deterministic orchestrator. Given an EvidenceContext
// (already-collected frame signals, no re-OCR, no cloud):
//
//   1. run evidence providers (A/B) -> raw records
//   2. generate candidates (C) from RAW text + classifier verdicts
//   3. normalize + deduplicate + capture conflict (B/info quality)
//   4. fuse candidate votes using normalized, weighted evidence
//   5. apply temporal consistency
//   6. decide uncertainty (insufficient / ambiguous / conflicting
//      / low-confidence / confident)
//   7. choose a deterministic primary category ONLY when it is
//      unambiguous; otherwise report the uncertainty state
//   8. attach a compact trace (G)
//
// Determinism: inputs (modelVersion + configurationVersion +
// evidence) always map to byte-identical output. There is no
// randomness and no time dependence (8B-1 section 33).
//
// A platform signal can RELAX contextual ambiguity but can never
// name a category by itself (8B-1 section 14).

class FusionEngine(
    private val config: FusionConfig = FusionConfig.DEFAULT,
    private val textProvider: EvidenceProvider = TextEvidenceProvider(),
    private val platformProvider: EvidenceProvider = PlatformEvidenceProvider(),
    private val interactionProvider: EvidenceProvider = InteractionEvidenceProvider(),
    private val visualProvider: EvidenceProvider = VisualEvidenceProvider(),
    private val temporalProvider: EvidenceProvider = TemporalEvidenceProvider()
) {

    private val normalizer = EvidenceNormalizer(config)

    /**
     * Runs the full fusion over an EvidenceContext.
     */
    fun predict(context: EvidenceContext): FusionPrediction {
        val frames = context.frames

        // ---------- Layer A/B: gather + normalize evidence ------
        val providerRecords = buildList {
            addAll(textProvider.provide(context))
            addAll(platformProvider.provide(context))
            addAll(interactionProvider.provide(context))
            addAll(visualProvider.provide(context))
            addAll(temporalProvider.provide(context))
        }
        val normalized = normalizer.deduplicate(providerRecords)
        val quality = normalizer.quality(context)

        // ---------- Layer C: candidates from RAW text -----------
        val candidateVotes = mutableListOf<CandidateVote>()
        val textVotes = mutableListOf<CandidateVote>()
        val classifierVotes = mutableListOf<CandidateVote>()
        var topic: String? = null
        var tone: String? = null
        var contentTypeHint: String? = null

        for (frame in frames) {
            val text = frame.ocrText
            if (!text.isNullOrBlank()) {
                val tv = CandidateGenerator.candidatesForText(
                    text, "TextEvidenceProvider", frame.frameId
                )
                candidateVotes += tv
                textVotes += tv
                topic = topic ?: CandidateGenerator.topicFor(text)
                tone = tone ?: CandidateGenerator.toneFor(text)
                contentTypeHint =
                    contentTypeHint ?: CandidateGenerator.contentTypeHint(text)
            }
            // Wrap the legacy classifier's own verdict, when the
            // caller supplied it, as one equal-vote CLASSIFIER signal.
            frame.existingCategory?.let { cat ->
                if (CategoryCatalog.normalize(cat) != null) {
                    val vote = CandidateVote(
                        category = cat,
                        vote = 0.8,
                        source = "LegacyClassifier",
                        frameId = frame.frameId
                    )
                    candidateVotes += vote
                    classifierVotes += vote
                }
            }
        }

        // ---------- fuse votes --------------------------------
        // Score per category = sum over its votes of
        //   (vote) * (evidenceNormalizedScore for that vote's
        //    source text evidence, else a baseline)
        //
        // Because multiple candidates come from the same OCR
        // record, we weight each vote by the normalized score of
        // the text evidence available on that frame, but only once
        // (deduplication in the text records prevents inflation).
        val perCategory = linkedMapOf<String, MutableList<Double>>()
        val textScoreByFrame = textScoreByFrame(normalized)

        candidateVotes.forEach { v ->
            val norm = CategoryCatalog.normalize(v.category) ?: return@forEach
            val base = textScoreByFrame[v.frameId] ?: 0.2
            val weighted = v.vote * (0.5 + 0.5 * base)
            perCategory.getOrPut(norm) { mutableListOf() } += weighted
        }

        val info = quality.hasInformativeText

        val fused: Map<String, Double> = perCategory.mapValues { (_, votes) ->
            // Mean of per-frame votes keeps one verbose frame from
            // dominating, reflects temporal distribution, and is
            // still bounded in [0,1].
            val mean = votes.averageOrZero()
            mean * config.textWeight
        }.toSortedMap()

        // ---------- temporal consistency ------------------------
        val consistent =
            frames.size >= config.temporalAgreeFrames && fused.isNotEmpty()

        val scored = fused.mapValues { (cat, score) ->
            // Temporal bonus only for the higher-scoring category
            // when frames agree; this rewards cross-frame agreement
            // without letting it create a category from nothing.
            val isLeader = cat == leader(fused)
            if (consistent && isLeader) score + config.temporalAgreeBonus else score
        }.toSortedMap()

        // ---------- decision / uncertainty ----------------------
        val mass = scored.values.sum()
        val conflict = detectConflict(
            scored = scored,
            textVotes = textVotes,
            classifierVotes = classifierVotes,
            frames = frames
        )

        val decision = decide(
            scored = scored,
            mass = mass,
            conflict = conflict,
            hasInformativeText = info,
            hasCandidates = scored.isNotEmpty(),
            frames = frames.size
        )

        return finalize(decision, scored, frames, providerRecords, quality)
    }

    /**
     * Candidate-aware conflict analysis. A conflict is surfaced
     * only when two INFORMATIVE, non-context signals disagree
     * about the leading category:
     *
     *   1. an explicit legacy-classifier verdict (CLASSIFIER
     *      family) that contradicts the leading TEXT-derived
     *      candidate, or
     *   2. strong cross-frame text disagreement (two frames point
     *      at different categories with strong support).
     *
     * Platform is always CONTEXT and can never cause a conflict
     * (8B-1 section 14).
     */
    private fun detectConflict(
        scored: Map<String, Double>,
        textVotes: List<CandidateVote>,
        classifierVotes: List<CandidateVote>,
        frames: List<FrameSignals>
    ): ConflictAnalysis {
        // The leading TEXT-supported candidate (ignoring the
        // classifier vote) and the classifier's verdict.
        val textLeader = textLeader(textVotes)
        val classifierLeader = classifierVotes
            .mapNotNull { CategoryCatalog.normalize(it.category) }
            .sortedBy { CategoryCatalog.keys.indexOf(it) }
            .firstOrNull()

        var classifierDisagrees =
            textLeader != null &&
                classifierLeader != null &&
                textLeader != classifierLeader

        // Cross-frame TEXT disagreement: any two frames that each
        // firmly lead toward a DIFFERENT domain. Repeated identical
        // frames (same content) never count as a conflict - that is
        // the same signal, not a disagreement (8B-1 section 19/44).
        val perFrameLeaders = frames.mapNotNull { frame ->
            textLeader(textVotes.filter { it.frameId == frame.frameId })
        }.distinct()
        val leaderDomains = perFrameLeaders
            .mapNotNull { CategoryCatalog.domainOf(it) }
            .distinct()
        val textDisagrees =
            perFrameLeaders.size >= 2 && leaderDomains.size >= 2

        val conflicting = classifierDisagrees || textDisagrees

        return ConflictAnalysis(
            conflicting = conflicting,
            classifierDisagrees = classifierDisagrees,
            textDisagrees = textDisagrees
        )
    }

    private fun textLeader(
        textVotes: List<CandidateVote>
    ): String? {
        return textVotes
            .groupBy { it.category }
            .mapValues { (_, votes) -> votes.sumOf { it.vote } }
            .maxByOrNull { it.value }
            ?.key
            ?: textVotes.firstOrNull()?.category
    }

    private data class ConflictAnalysis(
        val conflicting: Boolean,
        val classifierDisagrees: Boolean = false,
        val textDisagrees: Boolean = false
    )

    // Returns: category, uncertainty, confidence
    private fun decide(
        scored: Map<String, Double>,
        mass: Double,
        conflict: ConflictAnalysis,
        hasInformativeText: Boolean,
        hasCandidates: Boolean,
        frames: Int
    ): Decision {

        // INSUFFICIENT_EVIDENCE (8B-1 section 13/39): absence of
        // text is NOT "unknown content" but "not enough observed".
        if (!hasInformativeText || !hasCandidates || mass < config.minEvidenceMass) {
            return Decision(null, Uncertainty.INSUFFICIENT_EVIDENCE, 0.0)
        }

        // CONFLICTING_EVIDENCE (8B-1 section 26/27): strong,
        // informative disagreement is surfaced, never silently
        // resolved.
        if (conflict.conflicting) {
            return Decision(null, Uncertainty.CONFLICTING_EVIDENCE,
                leafShare(scored))
        }

        // DOMAIN-CONSENSUS (8B-1 section 36): when several leaves of
        // the SAME domain collectively dominate the evidence, the
        // content type is decided at the domain level even though no
        // single LEAF clearly wins (e.g. cricket + sports + football
        // all point to "sports"). Leaves within one domain are finer
        // granularity of the same content, not conflicting content,
        // so this is not a real ambiguity.
        val domainShare = domainConsensus(scored)
        if (domainShare != null) {
            return Decision(
                category = domainShare.leafLeader,
                uncertainty = Uncertainty.CONFIDENT,
                confidence = domainShare.share
            )
        }

        val leaderKey = leader(scored) ?: return Decision(
            null, Uncertainty.INSUFFICIENT_EVIDENCE, 0.0)
        val leaderScore = scored.getValue(leaderKey)

        // MAD-agnostic gap check for ambiguity/tie (8B-1 section 33):
        // if runner-up is within ambiguityGapRatio of the leader,
        // do NOT pick arbitrarily.
        val runnerUp = scored.filterKeys { it != leaderKey }
            .values.maxOrNull() ?: 0.0
        val gap = (leaderScore - runnerUp) / leaderScore.coerceAtLeast(1e-6)

        val confidence = leafShare(scored)

        return if (gap <= config.ambiguityGapRatio) {
            Decision(leaderKey, Uncertainty.AMBIGUOUS, confidence)
        } else if (confidence < config.bandLow) {
            Decision(leaderKey, Uncertainty.LOW_CONFIDENCE, confidence)
        } else {
            Decision(leaderKey, Uncertainty.CONFIDENT, confidence)
        }
    }

    private data class DomainConsensus(
        val leafLeader: String,
        val share: Double
    )

    /**
     * Returns a decided domain when one domain's leaves clearly
     * dominate all others, along with the best leaf and the domain
     * share of evidence as a confidence estimate. Null when there
     * is no clear domain consensus.
     */
    private fun domainConsensus(
        scored: Map<String, Double>
    ): DomainConsensus? {
        if (scored.isEmpty()) return null

        val total = scored.values.sum().coerceAtLeast(1e-9)

        val byDomain = HashMap<String, Double>()
        val leafByDomain = HashMap<String, String>()
        scored.forEach { (cat, score) ->
            val domain = CategoryCatalog.domainOf(cat) ?: cat
            byDomain[domain] = byDomain.getOrDefault(domain, 0.0) + score
            val cur = leafByDomain[domain]
            if (cur == null || scored.getValue(cur) < score) {
                leafByDomain[domain] = cat
            }
        }

        val top = byDomain.maxByOrNull { it.value } ?: return null
        val topShare = top.value / total
        val second = byDomain.filterKeys { it != top.key }
            .values.maxOrNull() ?: 0.0
        val gap = (top.value - second) / top.value.coerceAtLeast(1e-9)

        // A domain remains "decided" only if it clearly leads the
        // next domain AND holds enough of the evidence share,
        // otherwise the ambiguity between contents stands.
        if (gap > config.ambiguityGapRatio &&
            topShare >= (config.minEvidenceMass * 2).coerceAtMost(1.0)
        ) {
            // Confidence reflects how decisively the DOMAIN (not a
            // single leaf) wins: high when the domain dominates.
            return DomainConsensus(
                leafLeader = leafByDomain.getValue(top.key),
                share = topShare.coerceIn(0.0, 1.0)
            )
        }
        return null
    }

    private fun leafShare(scored: Map<String, Double>): Double {
        val leader = leader(scored) ?: return 0.0
        val lead = scored.getValue(leader)
        val total = scored.values.sum().coerceAtLeast(1e-6)
        return (lead / total).coerceIn(0.0, 1.0)
    }

    private fun finalize(
        decision: Decision,
        scored: Map<String, Double>,
        frames: List<FrameSignals>,
        records: List<EvidenceRecord>,
        quality: InformationQuality
    ): FusionPrediction {
        val primary = decision.category
        val band = ConfidenceBand.fromConfidence(decision.confidence, config)

        val secondaries: List<String> = if (primary == null) {
            emptyList()
        } else {
            scored.keys
                .filter { it != primary }
                .sortedByDescending { scored.getValue(it) }
                .take(3)
                .mapNotNull { CategoryCatalog.normalize(it) }
        }

        // Content type comes only from an explicit text hint; it is
        // kept separate from category (8B-1 section 36).
        val contentType = frames.asSequence()
            .mapNotNull { it.ocrText }
            .mapNotNull { CandidateGenerator.contentTypeHint(it) }
            .firstOrNull()

        val platform = frames.mapNotNull { it.platform }
            .distinct()
            .firstOrNull()

        val interaction = interactionRecords(records)
            .firstOrNull()
            ?.let {
                if (it.type == "INTERACTION_PRESENT") {
                    InteractionEvidenceProvider.STATE_PRESENT
                } else {
                    InteractionEvidenceProvider.STATE_UNKNOWN
                }
            }
            ?: InteractionEvidenceProvider.STATE_UNKNOWN

        val candidateTrace = scored.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (cat, score) ->
                CandidateTraceEntry(
                    category = cat,
                    score = score,
                    frameAgreement = agreementFor(cat, frames)
                )
            }

        val evTrace = recordsToTrace(records)

        val temporalAgreement = when {
            frames.size < config.temporalAgreeFrames ->
                FusionTrace.TEMPORAL_INSUFFICIENT
            scored.isEmpty() -> FusionTrace.TEMPORAL_INSUFFICIENT
            else -> FusionTrace.TEMPORAL_CONSISTENT
        }

        val trace = FusionTrace(
            framesConsidered = frames.size,
            perProviderStrength = evTrace,
            candidates = candidateTrace,
            temporalAgreement = temporalAgreement,
            finalCategory = primary,
            confidenceBand = band.label,
            uncertainty = decision.uncertainty.label,
            informationQuality = quality.note,
            modelVersion = config.modelVersion
        )

        return FusionPrediction(
            primaryCategory = primary,
            secondaryCategories = secondaries,
            confidence = decision.confidence,
            band = band,
            uncertainty = decision.uncertainty,
            topic = frames.asSequence().mapNotNull { it.ocrText }
                .mapNotNull { CandidateGenerator.topicFor(it) }.firstOrNull(),
            tone = frames.asSequence().mapNotNull { it.ocrText }
                .mapNotNull { CandidateGenerator.toneFor(it) }.firstOrNull(),
            contentType = contentType,
            platform = platform,
            interactionState = interaction,
            modelVersion = config.modelVersion,
            configVersion = config.configurationVersion,
            modelState = config.modelState,
            trace = trace
        )
    }

    private fun leader(scored: Map<String, Double>): String? {
        return scored.maxByOrNull { it.value }?.key ?: scored.keys.firstOrNull()
    }

    private fun textScoreByFrame(
        normalized: List<NormalizedEvidence>
    ): Map<String, Double> {
        val map = HashMap<String, Double>()
        normalized.forEach { n ->
            if (n.record.family == EvidenceFamily.TEXT &&
                !n.record.isVirtual
            ) {
                n.record.frameIds.forEach { fid ->
                    map[fid] = map[fid]?.coerceAtLeast(n.normalizedScore)
                        ?: n.normalizedScore
                }
            }
        }
        return map
    }

    private fun interactionRecords(
        records: List<EvidenceRecord>
    ): List<EvidenceRecord> {
        return records
            .filter { it.family == EvidenceFamily.INTERACTION }
            .sortedBy { it.valueSummary }
    }

    private fun recordsToTrace(
        records: List<EvidenceRecord>
    ): List<EvidenceTraceEntry> {
        return records
            .groupBy { it.family to it.type }
            .map { (key, recs) ->
                val (family, type) = key
                EvidenceTraceEntry(
                    family = family.name,
                    type = type,
                    strength = recs.maxOfOrNull { it.strength }?.name
                        ?: EvidenceStrength.NONE.name,
                    frames = recs.flatMap { it.frameIds }.distinct().size,
                    source = recs.firstOrNull()?.source ?: ""
                )
            }
            .sortedBy { it.family }
    }

    private fun agreementFor(
        category: String,
        frames: List<FrameSignals>
    ): Int {
        var agree = 0
        frames.forEach { frame ->
            val text = frame.ocrText ?: return@forEach
            val c = CandidateGenerator.candidatesForText(
                text, "agreement", frame.frameId
            )
            if (c.any { CategoryCatalog.normalize(it.category) == category }) {
                agree++
            }
        }
        return agree
    }

    private data class Decision(
        val category: String?,
        val uncertainty: Uncertainty,
        val confidence: Double
    )

    private fun List<Double>.averageOrZero(): Double {
        if (isEmpty()) return 0.0
        return average()
    }
}
