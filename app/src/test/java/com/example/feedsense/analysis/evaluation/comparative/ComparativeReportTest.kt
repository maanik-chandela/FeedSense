package com.example.feedsense.analysis.evaluation.comparative

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Report serialization tests: determinism, version provenance,
 * and privacy (no raw item / prediction content embedded).
 */
class ComparativeReportTest {

    private fun sampleReport(): ComparativeReport {
        // 14 eligible pairs: 2 both-correct, 10 improvements (c=10),
        // 2 regressions (b=2) -> statistically significant improvement.
        val pairs = (0 until 14).map { i ->
            when {
                i < 2 -> Triple("food", "food", "food")
                i < 12 -> Triple("food", "sports", "food")
                else -> Triple("food", "food", "sports")
            }.let { (t, b, e) ->
                TestFixtures.pair("rep-$i", t, b, e)
            }
        }
        return ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(
                pairs = pairs,
                config = ComparativeConfig.DEFAULT,
                reportId = "cmp-test-1",
                datasetVersion = "ds-v1",
                baselineModelVersion = "baseline-v1",
                fusionVersion = "fusion-v1",
                decisionVersion = "item-decision-v1"
            )
        )
    }

    @Test
    fun `json is deterministic`() {
        val r = sampleReport()
        val a = r.toJson()
        val b = r.toJson()
        assertEquals(a, b)
    }

    @Test
    fun `json carries version provenance and population`() {
        val root = JSONObject(sampleReport().toJson())
        assertEquals("ds-v1", root.getString("datasetVersion"))
        assertEquals("baseline-v1",
            root.getString("baselineModelVersion"))
        assertEquals("fusion-v1", root.getString("fusionVersion"))
        assertEquals("item-decision-v1",
            root.getString("decisionVersion"))
        assertEquals(
            ComparativeReport.Verdict.EIGHT_B_IMPROVED.label,
            root.getJSONObject("conclusion").getString("verdict")
        )
        assertEquals(14, root.getJSONObject("population").getInt("totalPaired"))
    }

    @Test
    fun `json does not embed raw content`() {
        val json = sampleReport().toJson().lowercase()
        // No frame/screen/OCR text, no transcripts, no frame pixels.
        assertFalse(json.contains("screenshot"))
        assertFalse(json.contains("ocrtext"))
        assertFalse(json.contains("transcript"))
    }

    @Test
    fun `json includes mcnemar and effect`() {
        val root = JSONObject(sampleReport().toJson())
        val m = root.getJSONObject("mcnemar")
        assertTrue(m.getBoolean("sufficient"))
        assertTrue(m.has("pValue"))
        val e = root.getJSONObject("effectSize")
        assertTrue(e.getBoolean("sufficient"))
    }

    @Test
    fun `csv overall includes conclusion and metrics`() {
        val csv = ComparativeCsv.overallCsv(sampleReport())
        val lines = csv.trim().split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("conclusionVerdict"))
        assertTrue(lines[1].contains("EIGHT_B_IMPROVED"))
        assertTrue(lines[1].contains("ds-v1"))
    }

    @Test
    fun `csv strata row per stratum cell`() {
        val csv = ComparativeCsv.strataCsv(sampleReport())
        val lines = csv.trim().split("\n")
        assertTrue(lines[0].contains("stratification"))
        assertTrue(lines[0].contains("stratum"))
        // duration has a cell
        assertTrue(lines.drop(1).any { it.startsWith("duration") })
    }

    @Test
    fun `csv never contains raw content`() {
        val both =
            ComparativeCsv.overallCsv(sampleReport()) + "\n" +
                ComparativeCsv.strataCsv(sampleReport())
        val low = both.lowercase()
        assertFalse(low.contains("screenshot"))
        assertFalse(low.contains("ocrtext"))
        assertFalse(low.contains("transcript"))
    }
}
