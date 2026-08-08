package com.example.feedsense.model

data class AIAnalysis(

    val primaryTopic: String,

    val confidence: Float,

    val sentiment: String,

    val emotion: String,

    val advertisementDetected: Boolean,

    val brandName: String? = null

)