package com.example.feedsense.model

data class ReviewItem(

    val feedItemId: String,

    val aiPrediction: String,

    val confidence: Float,

    val correctedCategory: String? = null,

    val correctionReason: String? = null,

    val reviewed: Boolean = false

)