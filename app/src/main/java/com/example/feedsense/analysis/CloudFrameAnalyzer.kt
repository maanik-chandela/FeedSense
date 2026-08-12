package com.example.feedsense.analysis
import java.io.File

// --------------------------------
// CLOUD FRAME ANALYZER
// --------------------------------
//
// Boundary for the cloud/reference AI.
//
// The cloud analyzer is only used as a fallback
// when the local pipeline is not confident.
//
// Budget rule: minimal API usage per active user.
// Real implementations must be careful about how
// often analyze() is called.
//
// Returns null when cloud analysis is unavailable
// or not configured. The pipeline then queues the
// frame for review instead of failing.
//

interface CloudFrameAnalyzer {

    suspend fun analyze(
        file: File
    ): FrameAnalysisResult?
}

// --------------------------------
// UNCONFIGURED CLOUD ANALYZER
// --------------------------------
//
// Milestone 7A placeholder.
//
// No cloud endpoint is configured yet, so this
// always reports unavailability. The pipeline
// architecture still exercises the full path
// (local -> gate -> fallback attempt).
//
// Swap this for a real implementation later.
//

class UnconfiguredCloudAnalyzer :
    CloudFrameAnalyzer {

    override suspend fun analyze(
        file: File
    ): FrameAnalysisResult? {

        return null
    }
}
