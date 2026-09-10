package com.example.feedsense.analysis.ml.preprocess.golden

// --------------------------------
// GOLDEN FIXTURE CORPUS (8B-15-4 §3, §4, §9)
// --------------------------------
//
// The versioned collection of all golden fixtures. The corpus
// version changes when fixtures are added/removed/modified,
// independently of the preprocessing implementation version.
//
// Versioning hierarchy:
//   - corpusVersion: changes when the fixture set changes
//   - fixtureVersion: per-fixture version for individual changes
//   - preprocessingVersion: the preprocessing implementation
//
// A preprocessing change should NOT silently rewrite fixture
// golden outputs. Instead:
//   1. tests fail
//   2. the diff is inspectable
//   3. a developer explicitly regenerates the golden output
//   4. fixtureVersion or corpusVersion is updated
//   5. the change is documented

/**
 * The golden fixture corpus: a versioned, immutable collection
 * of reference preprocessing cases.
 */
object GoldenFixtureCorpus {

    /**
     * Corpus version. Increment when fixtures are added, removed,
     * or their definitions change. Do NOT increment when only the
     * preprocessing implementation changes (that changes the
     * preprocessing version, not the fixture version).
     */
    const val CORPUS_VERSION = "golden-corpus-v1"

    /**
     * The preprocessing version this corpus was generated against.
     * Used for documentation; does NOT gate test execution.
     */
    const val REFERENCE_PREPROCESSING_VERSION = "preprocess-v2"
}
