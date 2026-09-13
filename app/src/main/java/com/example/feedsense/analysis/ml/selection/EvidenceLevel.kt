package com.example.feedsense.analysis.ml.selection

// --------------------------------
// EVIDENCE LEVEL (Milestone 8B-15-1)
// --------------------------------
//
// Every claim in the model/runtime selection process is graded
// by how it was obtained. Nothing is asserted as fact unless
// there is a reproducible basis for it (8B-15-1 decision rule:
// "no fabricated numbers"). The vocabulary is the ONLY one the
// selection structures use; numeric figures that could not be
// verified/measured must be `UNKNOWN` or `REQUIRES_EXPERIMENT`.

/*
 * How a claim was obtained.
 *
 * Ordering is by strength, weakest first, so stable sorting and
 * rollup ("what is the best evidence backing this candidate?")
 * are deterministic.
 *
 *   FACT                 - true by construction / definition
 *                          (e.g. "this enum has 7 values").
 *   MEASURED             - produced by a measurement we
 *                          control and can reproduce
 *                          (e.g. a benchmark we ran).
 *   DOCUMENTED_BY_SOURCE - read from a named, checkable source
 *                          (paper, official docs, Maven page).
 *   ENGINEERING_ESTIMATE - derived from published building
 *                          blocks with transparent math and a
 *                          stated assumption (never presented
 *                          as measured).
 *   HYPOTHESIS           - reasoning/expectation, no source.
 *   UNKNOWN              - we do not know and are not hiding it.
 *   REQUIRES_EXPERIMENT  - can only be answered on-device /
 *                          against the real status-quo baseline;
 *                          unresolved in 8B-15-1.
 */
enum class EvidenceLevel(
    val label: String,
    val description: String,
    val strengthOrder: Int
) {
    REQUIRES_EXPERIMENT(
        "REQUIRES_EXPERIMENT",
        "Unresolved in this phase; answerable only via an on-device or corpus experiment.",
        0
    ),
    UNKNOWN(
        "UNKNOWN",
        "Not known; no claim is made.",
        1
    ),
    HYPOTHESIS(
        "HYPOTHESIS",
        "Reasoning/expectation without a named source.",
        2
    ),
    ENGINEERING_ESTIMATE(
        "ENGINEERING_ESTIMATE",
        "Derived from published components with transparent assumptions; not measured.",
        3
    ),
    DOCUMENTED_BY_SOURCE(
        "DOCUMENTED_BY_SOURCE",
        "Read from a named, checkable source (paper, official docs, package page).",
        4
    ),
    MEASURED(
        "MEASURED",
        "Produced by a reproducible measurement we control.",
        5
    ),
    FACT(
        "FACT",
        "True by construction / definition.",
        6
    );

    companion object {
        /*
         * Stable identifier = label, so serialized references to
         * levels never drift with enum declaration order.
         */
        fun fromLabel(label: String): EvidenceLevel? =
            entries.firstOrNull { it.label == label }
    }
}