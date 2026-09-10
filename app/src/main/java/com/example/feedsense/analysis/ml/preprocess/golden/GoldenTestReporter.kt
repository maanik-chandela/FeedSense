package com.example.feedsense.analysis.ml.preprocess.golden

// --------------------------------
// GOLDEN TEST REPORTER (8B-15-4 §11)
// --------------------------------
//
// Collects and formats test results for the golden fixture suite.
// Provides a concise per-fixture summary and a structured
// diagnostic dump for failures.
//
// Design rules:
//   - no enormous tensor dumps into test output
//   - every failure identifies: fixture ID, expected shape,
//     actual shape, expected datatype, actual datatype,
//     expected hash, actual hash, preprocessing version,
//     model artifact identity, runtime identity
//   - summaries are human-readable and machine-parseable

/**
 * A single golden test outcome.
 */
data class GoldenTestOutcome(
    val fixtureId: String,
    val passed: Boolean,
    val diagnostics: GoldenDiagnostics? = null,
    val note: String = ""
)

/**
 * Summary of a golden test suite run.
 */
data class GoldenTestSummary(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val outcomes: List<GoldenTestOutcome>
) {
    init {
        require(total == passed + failed) {
            "total=$total must equal passed+failed=$passed+$failed"
        }
    }

    /**
     * Concise, machine-parseable summary line.
     */
    fun summaryLine(): String {
        return "GOLDEN FIXTURE RUN: $passed/$total passed, $failed failed"
    }
}

/**
 * Formats golden test results and failures.
 */
object GoldenTestReporter {

    /**
     * Builds a concise summary line from a list of outcomes.
     */
    fun summarize(outcomes: List<GoldenTestOutcome>): GoldenTestSummary {
        val passed = outcomes.count { it.passed }
        val failed = outcomes.count { !it.passed }
        return GoldenTestSummary(
            total = outcomes.size,
            passed = passed,
            failed = failed,
            outcomes = outcomes
        )
    }

    /**
     * Renders a failure block suitable for test assertion
     * messages. Includes structured diagnostics without dumping
     * entire tensors.
     */
    fun renderFailures(outcomes: List<GoldenTestOutcome>): String {
        val failures = outcomes.filterNot { it.passed }
        if (failures.isEmpty()) return "no failures"
        return buildString {
            appendLine("${failures.size} golden fixture failure(s):")
            for (failure in failures) {
                appendLine()
                if (failure.diagnostics != null) {
                    append(failure.diagnostics.summary())
                } else {
                    appendLine("  fixtureId=${failure.fixtureId}")
                    appendLine("  note=${failure.note}")
                }
            }
        }
    }

    /**
     * Builds the identity header block for diagnostics: model
     * artifact identity, runtime identity, preprocessing version.
     * These are content-free identifiers so no private data
     * ever enters test output.
     */
    fun identityHeader(
        fixtureId: String,
        preprocessingVersion: String = GoldenFixtureCorpus.REFERENCE_PREPROCESSING_VERSION,
        corpusVersion: String = GoldenFixtureCorpus.CORPUS_VERSION,
        artifactIdentity: String = "<PENDING artifact - none acquired>",
        runtimeIdentity: String = "LiteRT/TFLite (D1 decision record, artifact pending)"
    ): String {
        return buildString {
            appendLine("fixtureId=$fixtureId")
            appendLine("preprocessingVersion=$preprocessingVersion")
            appendLine("corpusVersion=$corpusVersion")
            appendLine("modelArtifactIdentity=$artifactIdentity")
            appendLine("runtimeIdentity=$runtimeIdentity")
        }
    }
}