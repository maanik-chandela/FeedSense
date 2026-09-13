package com.example.feedsense.analysis.ml.runtime.litert

import java.io.File

// --------------------------------
// LABEL MAPPING CONTRACT (8B-15-9, Phase 5)
// --------------------------------
//
// The empirical label ordering for MobileNetV2 1.0 224.
//
// CRITICAL: This mapping must be verified against the actual
// model artifact. The label ordering is NOT assumed -- it is
// derived from the official TensorFlow model documentation
// and the standard ImageNet label set.
//
// MobileNetV2 uses the standard ImageNet-1K label ordering:
//   - Index 0: background (not an ImageNet class)
//   - Index 1: tench (n01440764)
//   - Index 2: goldfish (n01443537)
//   - ...
//   - Index 1000: toilet tissue (n04553703)
//
// Source: ILSVRC 2012 / ImageNet Large Scale Visual Recognition
// Challenge. The canonical label file is available at:
// https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt
//
// VERIFICATION: The label ordering is DOCUMENTED_BY_SOURCE
// (TensorFlow Hub model card, ImageNet documentation). It
// should be EMPIRICALLY VERIFIED by running a known image
// through the model and confirming the top-1 label matches
// the expected ImageNet class.

/**
 * Verification status of the label mapping.
 */
enum class LabelVerificationStatus(val label: String) {
    DOCUMENTED_BY_SOURCE("DOCUMENTED_BY_SOURCE"),
    EMPIRICALLY_VERIFIED("EMPIRICALLY_VERIFIED"),
    UNVERIFIED("UNVERIFIED")
}

/**
 * Label mapping contract for MobileNetV2 1.0 224.
 *
 * The canonical label file is ImageNetLabels.txt from the
 * TensorFlow models repository. It contains 1001 labels
 * (background + 1000 ImageNet classes).
 */
object MobileNetV2Labels {

    const val LABEL_COUNT = 1001
    const val SOURCE_URL =
        "https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt"

    /**
     * Expected SHA-256 of the canonical ImageNetLabels.txt file.
     *
     * VERIFICATION STATUS: UNVERIFIED
     * Must be verified by first researcher who downloads the file.
     */
    const val EXPECTED_FILE_SHA256 = "NEEDS_VERIFICATION"

    const val FIRST_LABEL = "background"
    const val LAST_LABEL = "toilet tissue"

    var verificationStatus: LabelVerificationStatus =
        LabelVerificationStatus.DOCUMENTED_BY_SOURCE
        private set

    fun markEmpiricallyVerified() {
        verificationStatus = LabelVerificationStatus.EMPIRICALLY_VERIFIED
    }

    /**
     * Representative subset of labels for testing.
     *
     * These 20 labels span the first 300 ImageNet indices and
     * cover animals, objects, and the background class. Used
     * for JVM tests that cannot load the full label file.
     */
    val REPRESENTATIVE_TEST_LABELS: List<String> = listOf(
        "background",      // 0
        "tench",           // 1
        "goldfish",        // 2
        "great_white_shark", // 3
        "tiger_shark",     // 4
        "hammerhead",      // 5
        "electric_ray",    // 6
        "stingray",        // 7
        "cock",            // 8
        "hen",             // 9
        "ostrich",         // 10
        "brambling",       // 11
        "goldfinch",       // 12
        "house_finch",     // 13
        "junco",           // 14
        "indigo_bunting",  // 15
        "robin",           // 16
        "bulbul",          // 17
        "jay",             // 18
        "magpie"           // 19
    )

    /**
     * Loads the full label list from the canonical
     * ImageNetLabels.txt file.
     *
     * Each line is one label. The file must contain exactly
     * LABEL_COUNT (1001) non-empty lines.
     *
     * @param file the ImageNetLabels.txt file
     * @return the label list, or null if the file is invalid
     */
    fun loadFromFile(file: File): List<String>? {
        if (!file.exists()) return null
        val lines = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.size != LABEL_COUNT) return null
        return lines
    }

    /**
     * Verifies that a loaded label list matches the expected
     * first/last labels and count.
     *
     * @return true if the label list is consistent
     */
    fun verifyLabelList(labels: List<String>): Boolean {
        if (labels.size != LABEL_COUNT) return false
        if (labels[0] != FIRST_LABEL) return false
        if (labels[LABEL_COUNT - 1] != LAST_LABEL) return false
        return true
    }
}
