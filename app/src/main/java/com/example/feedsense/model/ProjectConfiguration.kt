package com.example.feedsense.model
data class ProjectConfiguration(

    val captureReels: Boolean = true,

    val capturePosts: Boolean = true,

    val captureStories: Boolean = false,

    val captureAds: Boolean = true,

    val aiEnabled: Boolean = true,

    val ocrEnabled: Boolean = true,

    val saveScreenshots: Boolean = true,

    val reviewThreshold: Float = 0.80f,

    val autoExport: Boolean = false

)