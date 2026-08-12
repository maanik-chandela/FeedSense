package com.example.feedsense.analysis

// --------------------------------
// CLASSIFICATION RESULT
// --------------------------------
//
// Produced by the local heuristic classifier.
//
// - primaryCategory   : best matching category, or null
// - secondaryCategories : categories that also matched
// - confidence        : 0..1, null when nothing matched
// - ambiguityScore    : closeness between top categories.
//                       1.0 = impossible to separate.
// - topic             : subcategory within the primary
//                       category, e.g. "cricket" (7D-B)
// - tone              : mood/format of the content,
//                       e.g. EDUCATIONAL (7D-B)
//

data class ClassificationResult(
    val primaryCategory: String?,
    val secondaryCategories: List<String>,
    val confidence: Double?,
    val ambiguityScore: Double?,
    val topic: String? = null,
    val tone: String? = null
)

// --------------------------------
// TEXT HEURISTIC CLASSIFIER
// --------------------------------
//
// Milestone 7A lightweight on-device classifier.
//
// It classifies OCR text using keyword heuristics.
// It is intentionally simple:
//
// - free
// - offline
// - fast
// - swappable for a real model later
//
// The FrameAnalyzer interface is the replacement
// point, so this heuristic can be exchanged without
// touching the rest of the application.
//

class TextHeuristicClassifier {

    fun classify(
        text: String?
    ): ClassificationResult {

        if (text.isNullOrBlank()) {
            return emptyResult()
        }

        val normalized =
            text.lowercase()

        val categoryHits =
            CATEGORY_KEYWORDS.map { (category, keywords) ->

                val hits =
                    keywords.count { keyword ->
                        normalized.contains(keyword)
                    }

                category to hits
            }

        val totalHits =
            categoryHits
                .sumOf { it.second }

        if (totalHits == 0) {
            return emptyResult()
        }

        val ranked =
            categoryHits
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

        val primary =
            ranked.first().first

        val topHits =
            ranked.first().second

        val confidence =
            topHits.toDouble() /
                    totalHits.toDouble()

        val secondaryCategories =
            ranked
                .drop(1)
                .filter { it.second > 0 }
                .map { it.first }
                .take(MAX_SECONDARY_CATEGORIES)

        val ambiguityScore =
            if (ranked.size > 1) {

                val secondHits =
                    ranked[1].second

                secondHits.toDouble() /
                        topHits.toDouble()

            } else {
                0.0
            }

        val topic =
            primary?.let { category ->
                topicOf(
                    category = category,
                    normalized = normalized
                )
            }

        val tone =
            toneOf(normalized)

        return ClassificationResult(
            primaryCategory = primary,
            secondaryCategories = secondaryCategories,
            confidence = confidence,
            ambiguityScore = ambiguityScore,
            topic = topic,
            tone = tone
        )
    }

    // ========================================
    // TOPIC EXTRACTION (7D-B)
    // ========================================
    //
    // Within the primary category, find the most
    // specific subcategory the text matches.
    // e.g. sports + "ipl", "wicket" -> "cricket".
    //

    private fun topicOf(
        category: String,
        normalized: String
    ): String? {

        val topicHits =
            TOPIC_KEYWORDS[category]
                ?.map { (topic, keywords) ->

                    val hits =
                        keywords.count { keyword ->
                            normalized.contains(keyword)
                        }

                    topic to hits
                }
                ?.filter {
                    it.second > 0
                }
                ?: return null

        val best =
            topicHits
                .maxByOrNull {
                    it.second
                }
                ?: return null

        return best.first
    }

    // ========================================
    // TONE EXTRACTION (7D-B)
    // ========================================
    //
    // Independent of the category: the mood/format
    // of the content inferred from language.
    //

    private fun toneOf(
        normalized: String
    ): String? {

        return TONE_KEYWORDS
            .entries
            .firstOrNull { (_, keywords) ->

                keywords.any { keyword ->
                    normalized.contains(keyword)
                }
            }
            ?.key
    }

    private fun emptyResult(): ClassificationResult {
        return ClassificationResult(
            primaryCategory = null,
            secondaryCategories = emptyList(),
            confidence = null,
            ambiguityScore = null,
            topic = null,
            tone = null
        )
    }

    companion object {

        private const val MAX_SECONDARY_CATEGORIES = 3

        // --------------------------------
        // TONE CONSTANTS (7D-B)
        // --------------------------------

        const val TONE_EDUCATIONAL =
            "EDUCATIONAL"

        const val TONE_COMEDIC =
            "COMEDIC"

        const val TONE_INSPIRATIONAL =
            "INSPIRATIONAL"

        const val TONE_NEWS =
            "NEWS"

        const val TONE_MUSICAL =
            "MUSICAL"

        const val TONE_ENTERTAINMENT =
            "ENTERTAINMENT"

        const val TONE_GAMING =
            "GAMING"

        // --------------------------------
        // CATEGORY KEYWORDS
        // --------------------------------

        val CATEGORY_KEYWORDS: Map<String, List<String>> =
            mapOf(
                "sports" to listOf(
                    "cricket", "football", "goal", "ipl", "match",
                    "score", "player", "highlight", "six", "wicket",
                    "sports", "team", "tournament", "stadium"
                ),
                "comedy" to listOf(
                    "funny", "laugh", "meme", "prank", "comedy",
                    "joke", "roast", "skit", "hilarious", "fun"
                ),
                "motivation" to listOf(
                    "motivation", "success", "mindset", "discipline",
                    "never give up", "hustle", "inspiration",
                    "hard work", "dream", "self improvement"
                ),
                "education" to listOf(
                    "learn", "study", "science", "history", "facts",
                    "tutorial", "explain", "lesson", "knowledge",
                    "math", "physics", "chemistry", "education"
                ),
                "news" to listOf(
                    "news", "breaking", "live", "update", "headline",
                    "alert", "report", "election", "government"
                ),
                "music" to listOf(
                    "song", "music", "lyrics", "album", "artist",
                    "remix", "concert", "singer", "track", "audio"
                ),
                "gaming" to listOf(
                    "game", "gaming", "gameplay", "level", "player",
                    "minecraft", "pubg", "fortnite", "gta", "rank"
                ),
                "fitness" to listOf(
                    "workout", "fitness", "gym", "exercise", "yoga",
                    "diet", "muscle", "weight loss", "protein", "training"
                ),
                "technology" to listOf(
                    "tech", "phone", "android", "iphone", "ai",
                    "gadget", "review", "laptop", "software", "robot"
                ),
                "fashion" to listOf(
                    "fashion", "style", "outfit", "ootd", "trend",
                    "brand", "clothes", "look", "styling"
                ),
                "food" to listOf(
                    "recipe", "food", "cooking", "tasty", "restaurant",
                    "ingredient", "kitchen", "biryani", "dessert", "chef"
                )
            )

        // --------------------------------
        // TOPIC KEYWORDS (7D-B)
        // --------------------------------
        //
        // Subcategories inside each primary category.
        // Used to answer "what exactly was it about?"
        // e.g. sports -> cricket / football / tennis.
        //

        val TOPIC_KEYWORDS:
                Map<String, Map<String, List<String>>> =
            mapOf(
                "sports" to mapOf(
                    "cricket" to listOf(
                        "cricket", "ipl", "wicket", "six", "bowler",
                        "batting", "innings", "batsman", "toss",
                        "overs", "century", "bcci"
                    ),
                    "football" to listOf(
                        "football", "soccer", "goal", "fifa", "premier",
                        "striker", "goalkeeper", "corner", "penalty",
                        "euro", "world cup"
                    ),
                    "tennis" to listOf(
                        "tennis", "grand slam", "serve", "set point",
                        "federer", "nadal", "djokovic", "wimbledon"
                    ),
                    "basketball" to listOf(
                        "basketball", "nba", "dunk", "hoop", "lebron",
                        "three pointer", "free throw"
                    ),
                    "motorsport" to listOf(
                        "formula", "f1", "grand prix", "moto gp",
                        "racing", "verstappen", "hamilton"
                    ),
                    "boxing" to listOf(
                        "boxing", "knockout", "fight", "heavyweight",
                        "mma", "ufc"
                    )
                ),
                "motivation" to mapOf(
                    "self improvement" to listOf(
                        "self improvement", "mindset", "discipline",
                        "habits", "growth"
                    ),
                    "career" to listOf(
                        "success", "hustle", "entrepreneur", "job",
                        "business", "wealth", "earn"
                    ),
                    "study" to listOf(
                        "study", "exams", "student", "focus",
                        "revision", "topper"
                    ),
                    "fitness goals" to listOf(
                        "fitness goals", "weight loss", "muscle",
                        "workout", "body"
                    )
                ),
                "education" to mapOf(
                    "science" to listOf(
                        "science", "physics", "chemistry", "biology",
                        "space", "experiment", "quantum"
                    ),
                    "history" to listOf(
                        "history", "ancient", "war", "king", "empire",
                        "civilisation", "revolution"
                    ),
                    "math" to listOf(
                        "math", "mathematics", "algebra", "calculus",
                        "geometry", "equation", "statistics"
                    ),
                    "geography" to listOf(
                        "geography", "country", "map", "river",
                        "mountain", "capital"
                    )
                ),
                "technology" to mapOf(
                    "smartphones" to listOf(
                        "smartphone", "iphone", "android", "galaxy",
                        "pixel", "phone"
                    ),
                    "ai" to listOf(
                        "ai", "artificial intelligence", "machine learning",
                        "chatbot", "gpt", "neural"
                    ),
                    "gadgets" to listOf(
                        "gadget", "laptop", "earbuds", "headphones",
                        "smartwatch", "tablet"
                    ),
                    "software" to listOf(
                        "software", "app", "coding", "programming",
                        "developer", "cloud", "startup"
                    )
                ),
                "food" to mapOf(
                    "cooking" to listOf(
                        "cooking", "recipe", "kitchen", "chef",
                        "bake", "fry", "cook"
                    ),
                    "street food" to listOf(
                        "street food", "biryani", "chaat", "pizza",
                        "burger", "street"
                    ),
                    "restaurants" to listOf(
                        "restaurant", "cafe", "review", "menu",
                        "delivery", "zomato", "swiggy"
                    ),
                    "desserts" to listOf(
                        "dessert", "cake", "chocolate", "ice cream",
                        "sweet"
                    )
                ),
                "music" to mapOf(
                    "bollywood" to listOf(
                        "bollywood", "soundtrack", "song", "singer",
                        "composer"
                    ),
                    "pop" to listOf(
                        "pop", "chart", "billboard", "top 40"
                    ),
                    "hip hop" to listOf(
                        "hip hop", "rap", "rapper", "drill"
                    ),
                    "live" to listOf(
                        "concert", "live", "tour", "performance"
                    )
                ),
                "gaming" to mapOf(
                    "battle royale" to listOf(
                        "pubg", "fortnite", "battle royale", "free fire"
                    ),
                    "minecraft" to listOf(
                        "minecraft", "creeper", "survival"
                    ),
                    "gta" to listOf(
                        "gta", "grand theft auto"
                    ),
                    "esports" to listOf(
                        "esports", "tournament", "rank", "competitive",
                        "league"
                    )
                ),
                "fitness" to mapOf(
                    "workout" to listOf(
                        "workout", "training", "exercise", "reps"
                    ),
                    "yoga" to listOf(
                        "yoga", "meditation", "stretching", "asana"
                    ),
                    "nutrition" to listOf(
                        "diet", "protein", "nutrition", "calories",
                        "meal plan"
                    ),
                    "bodybuilding" to listOf(
                        "bodybuilding", "muscle", "gym", "biceps"
                    )
                ),
                "fashion" to mapOf(
                    "outfits" to listOf(
                        "outfit", "ootd", "look", "wardrobe",
                        "styling"
                    ),
                    "trends" to listOf(
                        "trend", "collection", "runway", "season"
                    ),
                    "shopping" to listOf(
                        "shopping", "haul", "brand", "discount",
                        "sale"
                    )
                )
            )

        // --------------------------------
        // TONE KEYWORDS (7D-B)
        // --------------------------------

        val TONE_KEYWORDS:
                Map<String, List<String>> =
            mapOf(
                TONE_EDUCATIONAL to listOf(
                    "learn", "how to", "tutorial", "explain",
                    "facts", "guide", "tips", "lesson", "course",
                    "study", "science", "history", "understand"
                ),
                TONE_INSPIRATIONAL to listOf(
                    "motivation", "inspiration", "never give up",
                    "success", "hustle", "dream", "mindset",
                    "believe in yourself", "discipline"
                ),
                TONE_COMEDIC to listOf(
                    "funny", "joke", "meme", "prank", "comedy",
                    "roast", "laugh", "hilarious", "skit"
                ),
                TONE_NEWS to listOf(
                    "news", "breaking", "headline", "report",
                    "alert", "update", "election", "breaking news"
                ),
                TONE_MUSICAL to listOf(
                    "song", "music", "lyrics", "album", "remix",
                    "audio", "soundtrack", "sing"
                ),
                TONE_GAMING to listOf(
                    "gameplay", "gaming", "esports", "tournament",
                    "rank", "level up", "stream"
                ),
                TONE_ENTERTAINMENT to listOf(
                    "viral", "trending", "challenge", "dance",
                    "reaction", "entertainment", "exclusive"
                )
            )
    }
}