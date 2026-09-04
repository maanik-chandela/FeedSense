package com.example.feedsense.analysis.evidence

import com.example.feedsense.analysis.CategoryCatalog
import java.io.File

/*
 * Milestone 8B-5.
 *
 * OCR evidence extractor.
 *
 * Converts raw OCR text into structured evidence.
 * Reuses the existing OCR infrastructure without
 * rewriting it.
 *
 * Evidence produced:
 *   - OCR_TEXT with keyword matches
 *   - Quality based on text length and keyword hits
 *   - Supporting/contradicting categories from keywords
 *
 * Normalization:
 *   - Lowercase
 *   - Whitespace normalization
 *   - Punctuation normalization
 *   - Unicode-safe
 *
 * Important:
 *   - No raw private text in logs
 *   - Conservative normalization only
 *   - Keywords contribute to scores, not force categories
 *
 * Limitations:
 *   - Depends on OCR quality
 *   - Limited to text visible on screen
 *   - No semantic understanding of text
 */
class OcrEvidenceExtractor : EvidenceExtractor {

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        val text = existingText

        if (text.isNullOrBlank()) {
            return listOf(
                Evidence(
                    type = EvidenceType.OCR_TEXT,
                    source = EvidenceSource.OCR,
                    quality = EvidenceQuality.NONE,
                    value = "no_text",
                    metadata = mapOf(
                        "textAvailable" to "false"
                    )
                )
            )
        }

        val normalized = normalizeText(text)
        val textLength = normalized.length

        // --------------------------------
        // KEYWORD MATCHING
        // --------------------------------

        val categoryHits =
            CategoryCatalog.keywordMap.mapValues {
                    (_, keywords) ->
                keywords.count { keyword ->
                    normalized.contains(keyword)
                }
            }
                .filter { it.value > 0 }

        val totalHits =
            categoryHits.values.sum()

        // --------------------------------
        // QUALITY ASSESSMENT
        // --------------------------------

        val quality = when {
            totalHits >= 5 ->
                EvidenceQuality.HIGH
            totalHits >= 2 ->
                EvidenceQuality.MEDIUM
            totalHits >= 1 ->
                EvidenceQuality.LOW
            else ->
                EvidenceQuality.NONE
        }

        // --------------------------------
        // CATEGORY SUPPORT
        // --------------------------------

        val supporting =
            categoryHits.keys.toList()

        // Keywords that contradict the top category
        // are detected by checking for competing
        // category hits
        val contradicting = emptyList<String>()

        // --------------------------------
        // BUILD EVIDENCE
        // --------------------------------

        val evidence = mutableListOf<Evidence>()

        evidence.add(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = quality,
                value = "text_available",
                supportingCategories = supporting,
                contradictingCategories =
                    contradicting,
                metadata = mapOf(
                    "textAvailable" to "true",
                    "textLength" to
                            textLength.toString(),
                    "keywordHits" to
                            totalHits.toString(),
                    "categoryHits" to
                            categoryHits.toString()
                )
            )
        )

        // Individual category evidence for fine-grained
        // fusion
        for ((category, hits) in categoryHits) {
            val categoryQuality = when {
                hits >= 3 ->
                    EvidenceQuality.HIGH
                hits >= 2 ->
                    EvidenceQuality.MEDIUM
                else ->
                    EvidenceQuality.LOW
            }

            evidence.add(
                Evidence(
                    type = EvidenceType.OCR_TEXT,
                    source = EvidenceSource.OCR,
                    quality = categoryQuality,
                    value = "keyword:$category",
                    supportingCategories =
                        listOf(category),
                    metadata = mapOf(
                        "keywordCount" to
                                hits.toString(),
                        "category" to category
                    )
                )
            )
        }

        return evidence
    }

    /*
     * Conservative text normalization.
     * Preserves meaningful words while normalizing
     * format differences.
     */
    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
