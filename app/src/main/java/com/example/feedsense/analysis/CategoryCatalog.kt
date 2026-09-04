package com.example.feedsense.analysis

// --------------------------------
// CATEGORY CATALOG
// --------------------------------
//
// Milestone 7D.
//
// Single data-driven registry of content categories.
//
// Categories are NOT a giant fixed list embedded in
// the classifier. New categories and subcategories can
// be added here without touching the classification
// logic, and future model-driven categories can be
// recorded through labeled reference data.
//
// Keys are stable lowercase identifiers (used in the
// database). displayName is what a human sees.
//
// Milestone 7U. Taxonomy expansion: the catalog now
// covers the full content taxonomy (domains AND
// subcategories) - sports, news, politics, education,
// technology, science, health, fitness, food, cooking,
// travel, finance, business, productivity, motivation,
// self-improvement, comedy, meme, entertainment, movie
// clips, series clips, music, music videos, edits,
// creator edits, youtuber edits, gaming, gameplay,
// esports, anime, animation, documentaries, podcasts,
// interviews, rankings, top lists, tutorials, how-tos,
// reviews, product reviews, advertising, sponsored
// content, influencer content, lifestyle, fashion,
// beauty, relationships, motivational speeches,
// storytelling, horror, crime, drama, romance, action,
// reaction, commentary, discussion, live streams,
// short/long videos, plus the "other"/"unknown"
// fallback buckets.
//
// Keyword sets stay conservative and non-overlapping on
// purpose so a text does not light up ten categories at
// once. When a text legitimately crosses families
// (e.g. a cooking recipe shown in a movie clip) the
// ambiguity is reported honestly instead of being
// forced into one label.
//

object CategoryCatalog {

    data class CategorySpec(
        val key: String,
        val displayName: String,
        val keywords: List<String>
    )

    private val SPECS: List<CategorySpec> = listOf(
        CategorySpec(
            key = "sports",
            displayName = "Sports",
            keywords = listOf(
                "cricket", "football", "goal", "ipl", "match",
                "score", "player", "highlight", "six", "wicket",
                "sports", "team", "tournament", "stadium"
            )
        ),
        CategorySpec(
            key = "comedy",
            displayName = "Comedy",
            keywords = listOf(
                "funny", "laugh", "prank", "comedy",
                "joke", "roast", "skit", "hilarious", "fun"
            )
        ),
        CategorySpec(
            key = "motivation",
            displayName = "Motivation",
            keywords = listOf(
                "motivation", "success", "mindset", "discipline",
                "never give up", "hustle", "inspiration",
                "hard work", "dream", "self improvement"
            )
        ),
        CategorySpec(
            key = "education",
            displayName = "Education",
            keywords = listOf(
                "learn", "study", "science", "history", "facts",
                "tutorial", "explain", "lesson", "knowledge",
                "math", "physics", "chemistry", "education"
            )
        ),
        CategorySpec(
            key = "news",
            displayName = "News",
            keywords = listOf(
                "news", "breaking", "live", "update", "headline",
                "alert", "report", "election", "government"
            )
        ),
        CategorySpec(
            key = "music",
            displayName = "Music",
            keywords = listOf(
                "song", "music", "lyrics", "album", "artist",
                "remix", "concert", "singer", "track", "audio"
            )
        ),
        CategorySpec(
            key = "gaming",
            displayName = "Gaming",
            keywords = listOf(
                "game", "gaming", "gameplay", "level", "player",
                "minecraft", "pubg", "fortnite", "gta", "rank"
            )
        ),
        CategorySpec(
            key = "fitness",
            displayName = "Fitness",
            keywords = listOf(
                "workout", "fitness", "gym", "exercise", "yoga",
                "diet", "muscle", "weight loss", "protein", "training"
            )
        ),
        CategorySpec(
            key = "technology",
            displayName = "Technology",
            keywords = listOf(
                "tech", "phone", "android", "iphone", "ai",
                "gadget", "review", "laptop", "software", "robot"
            )
        ),
        CategorySpec(
            key = "fashion",
            displayName = "Fashion",
            keywords = listOf(
                "fashion", "style", "outfit", "ootd", "trend",
                "brand", "clothes", "look", "styling"
            )
        ),
        CategorySpec(
            key = "food",
            displayName = "Food",
            keywords = listOf(
                "recipe", "food", "cooking", "tasty", "restaurant",
                "ingredient", "kitchen", "biryani", "dessert", "chef"
            )
        ),
        CategorySpec(
            key = "lifestyle",
            displayName = "Lifestyle",
            keywords = listOf(
                "lifestyle", "daily routine", "vlog", "minimalist",
                "home tour", "self care", "morning routine",
                "travel vlog", "productivity", "work from home",
                "day in my life"
            )
        ),
        CategorySpec(
            key = "finance",
            displayName = "Finance",
            keywords = listOf(
                "money", "invest", "stock", "mutual fund", "trading",
                "finance", "crypto", "bitcoin", "tax", "savings",
                "budget", "loan", "income", "sip", "nifty",
                "stock market"
            )
        ),
        CategorySpec(
            key = "other",
            displayName = "Other",
            keywords = emptyList()
        ),
        // --------------------------------
        // MILESTONE 7U TAXONOMY EXPANSION
        // --------------------------------
        //
        // Domains and subcategories added on top of the
        // classic 14. Keywords are conservative on purpose
        // so overlapping families (e.g. food/cooking,
        // motivation/self-improvement, gaming/gameplay/
        // esports) stay separable.
        // --------------------------------
        CategorySpec(
            key = "politics",
            displayName = "Politics",
            keywords = listOf(
                "politics", "political", "election", "government",
                "minister", "parliament", "vote", "president",
                "rally", "policy"
            )
        ),
        CategorySpec(
            key = "science",
            displayName = "Science",
            keywords = listOf(
                "science", "space", "nasa", "universe", "quantum",
                "experiment", "research", "gravity", "planet",
                "galaxy"
            )
        ),
        CategorySpec(
            key = "health",
            displayName = "Health",
            keywords = listOf(
                "health", "doctor", "hospital", "medicine",
                "mental health", "wellness", "disease", "symptom",
                "medical", "cure"
            )
        ),
        CategorySpec(
            key = "cooking",
            displayName = "Cooking",
            keywords = listOf(
                "cooking", "recipe", "kitchen", "chef", "bake",
                "fry", "ingredients", "meal prep", "homemade",
                "cook with me"
            )
        ),
        CategorySpec(
            key = "travel",
            displayName = "Travel",
            keywords = listOf(
                "travel", "trip", "destination", "tourist",
                "flight", "hotel", "backpacking", "vacation",
                "wanderlust", "visit"
            )
        ),
        CategorySpec(
            key = "business",
            displayName = "Business",
            keywords = listOf(
                "business", "startup", "entrepreneur", "company",
                "revenue", "ceo", "founder", "economy",
                "market share", "b2b"
            )
        ),
        CategorySpec(
            key = "productivity",
            displayName = "Productivity",
            keywords = listOf(
                "productivity", "focus", "time management",
                "deep work", "planner", "efficient",
                "productivity tips"
            )
        ),
        CategorySpec(
            key = "self_improvement",
            displayName = "Self Improvement",
            keywords = listOf(
                "self improvement", "personal growth",
                "growth mindset", "improve yourself",
                "better yourself", "self improvement tips"
            )
        ),
        CategorySpec(
            key = "meme",
            displayName = "Meme",
            keywords = listOf(
                "meme", "memes", "dank", "template",
                "meme compilation", "relatable", "meme review"
            )
        ),
        CategorySpec(
            key = "entertainment",
            displayName = "Entertainment",
            keywords = listOf(
                "entertainment", "celebrity", "gossip",
                "hollywood", "showbiz", "viral video",
                "trending", "challenge", "dance challenge",
                "exclusive", "viral"
            )
        ),
        CategorySpec(
            key = "movie_clip",
            displayName = "Movie Clip",
            keywords = listOf(
                "movie", "trailer", "film", "cinema", "teaser",
                "release date", "box office", "bollywood movie",
                "hollywood movie"
            )
        ),
        CategorySpec(
            key = "series_clip",
            displayName = "Series Clip",
            keywords = listOf(
                "series", "web series", "episode", "season",
                "netflix", "prime video", "next episode", "ott"
            )
        ),
        CategorySpec(
            key = "music_video",
            displayName = "Music Video",
            keywords = listOf(
                "music video", "mv", "official video",
                "visualizer", "audio video", "new song"
            )
        ),
        CategorySpec(
            key = "edit",
            displayName = "Edit",
            keywords = listOf(
                "editing", "video edit", "video editing",
                "edit tutorial", "edit trends", "edit compilation"
            )
        ),
        CategorySpec(
            key = "creator_edit",
            displayName = "Creator Edit",
            keywords = listOf(
                "creator edit", "after effects", "vfx",
                "viral edit", "ae edit", "fan edit",
                "transition edit"
            )
        ),
        CategorySpec(
            key = "youtuber_edit",
            displayName = "Youtuber Edit",
            keywords = listOf(
                "youtuber", "like and subscribe", "my channel",
                "subscriber", "daily upload", "youtube channel",
                "content creator"
            )
        ),
        CategorySpec(
            key = "gameplay",
            displayName = "Gameplay",
            keywords = listOf(
                "gameplay", "game play", "walkthrough",
                "let's play", "gameplay moments",
                "gameplay walkthrough"
            )
        ),
        CategorySpec(
            key = "esports",
            displayName = "Esports",
            keywords = listOf(
                "esports", "tournament", "competitive",
                "pro player", "championship", "league match",
                "esports tournament", "knockout"
            )
        ),
        CategorySpec(
            key = "anime",
            displayName = "Anime",
            keywords = listOf(
                "anime", "manga", "naruto", "one piece",
                "dragon ball", "jujutsu", "attack on titan",
                "anime edit"
            )
        ),
        CategorySpec(
            key = "animation",
            displayName = "Animation",
            keywords = listOf(
                "animation", "cartoon", "animated", "disney",
                "pixar", "animated movie", "kids cartoon"
            )
        ),
        CategorySpec(
            key = "documentary",
            displayName = "Documentary",
            keywords = listOf(
                "documentary", "docuseries", "real story",
                "behind the scenes", "documentary film",
                "true story"
            )
        ),
        CategorySpec(
            key = "podcast",
            displayName = "Podcast",
            keywords = listOf(
                "podcast", "podcast clip", "spotify",
                "podcast host", "guest episode"
            )
        ),
        CategorySpec(
            key = "interview",
            displayName = "Interview",
            keywords = listOf(
                "interview", "exclusive interview", "q&a",
                "celebrity interview", "guest interview",
                "interview clip"
            )
        ),
        CategorySpec(
            key = "ranking",
            displayName = "Ranking",
            keywords = listOf(
                "ranking", "top 10", "top 5", "best of",
                "ranked", "countdown", "listicle",
                "top ranking"
            )
        ),
        CategorySpec(
            key = "top_list",
            displayName = "Top List",
            keywords = listOf(
                "top list", "best list", "top 100", "top 20",
                "top 50", "must watch", "best picks"
            )
        ),
        CategorySpec(
            key = "tutorial",
            displayName = "Tutorial",
            keywords = listOf(
                "tutorial", "step by step", "beginner tutorial",
                "diy tutorial", "learn how to",
                "tutorial for beginners"
            )
        ),
        CategorySpec(
            key = "how_to",
            displayName = "How To",
            keywords = listOf(
                "how to", "how to make", "how to fix",
                "how to do", "tips and tricks",
                "step by step guide"
            )
        ),
        CategorySpec(
            key = "review",
            displayName = "Review",
            keywords = listOf(
                "review", "honest review", "my review",
                "first impressions", "in depth review", "verdict"
            )
        ),
        CategorySpec(
            key = "product_review",
            displayName = "Product Review",
            keywords = listOf(
                "product review", "gadget review", "unboxing",
                "phone review", "tech review", "camera review",
                "amazon product"
            )
        ),
        CategorySpec(
            key = "advertisement",
            displayName = "Advertisement",
            keywords = listOf(
                "ad", "advertisement", "buy now",
                "limited offer", "shop now", "promo code",
                "sale", "discount", "free shipping"
            )
        ),
        CategorySpec(
            key = "sponsored_content",
            displayName = "Sponsored Content",
            keywords = listOf(
                "sponsored content", "sponsored by",
                "paid promotion", "sponsored", "partnership",
                "brand deal", "collab"
            )
        ),
        CategorySpec(
            key = "influencer_content",
            displayName = "Influencer Content",
            keywords = listOf(
                "influencer", "influencer tips", "creator tips",
                "influencer marketing", "follow me",
                "brand collab", "content creator"
            )
        ),
        CategorySpec(
            key = "beauty",
            displayName = "Beauty",
            keywords = listOf(
                "beauty", "makeup", "skincare", "beauty tips",
                "lipstick", "foundation", "glow",
                "skincare routine", "serum"
            )
        ),
        CategorySpec(
            key = "relationships",
            displayName = "Relationships",
            keywords = listOf(
                "relationship", "dating", "couple", "breakup",
                "marriage", "relationship advice",
                "relationship goals", "love tips"
            )
        ),
        CategorySpec(
            key = "motivational_speech",
            displayName = "Motivational Speech",
            keywords = listOf(
                "motivational speech", "motivational video",
                "inspiration speech", "motivational quotes",
                "powerful speech", "speech about"
            )
        ),
        CategorySpec(
            key = "storytelling",
            displayName = "Storytelling",
            keywords = listOf(
                "story time", "storytelling", "true story",
                "my story", "short story", "narration",
                "storytelling tips"
            )
        ),
        CategorySpec(
            key = "horror",
            displayName = "Horror",
            keywords = listOf(
                "horror", "scary", "creepypasta", "horror movie",
                "ghost", "haunted", "jump scare"
            )
        ),
        CategorySpec(
            key = "crime",
            displayName = "Crime",
            keywords = listOf(
                "crime", "true crime", "murder", "crime story",
                "police", "investigation", "criminal case",
                "crime documentary"
            )
        ),
        CategorySpec(
            key = "drama",
            displayName = "Drama",
            keywords = listOf(
                "drama", "dramatic", "emotional", "sad story",
                "climax", "drama series", "soap opera"
            )
        ),
        CategorySpec(
            key = "romance",
            displayName = "Romance",
            keywords = listOf(
                "romance", "romantic", "love story",
                "couple goals", "romantic scene", "love scene"
            )
        ),
        CategorySpec(
            key = "action",
            displayName = "Action",
            keywords = listOf(
                "action movie", "action scene", "action film",
                "fight scene", "chase scene", "stunt", "thriller"
            )
        ),
        CategorySpec(
            key = "reaction",
            displayName = "Reaction",
            keywords = listOf(
                "reaction", "reacting", "reaction video",
                "first time watching", "responding to",
                "my reaction"
            )
        ),
        CategorySpec(
            key = "commentary",
            displayName = "Commentary",
            keywords = listOf(
                "commentary", "commentary video", "analysis",
                "my opinion", "breaking down", "talking about"
            )
        ),
        CategorySpec(
            key = "discussion",
            displayName = "Discussion",
            keywords = listOf(
                "discussion", "debate", "talk show", "panel",
                "conversation", "open discussion",
                "point of view"
            )
        ),
        CategorySpec(
            key = "live_stream",
            displayName = "Live Stream",
            keywords = listOf(
                "live stream", "livestream", "going live",
                "live streaming", "streaming now", "watch live",
                "live now"
            )
        ),
        CategorySpec(
            key = "short_video",
            displayName = "Short Video",
            keywords = listOf(
                "short video", "short form", "reels", "vertical video",
                "short clip", "shorts", "tiktok video"
            )
        ),
        CategorySpec(
            key = "long_video",
            displayName = "Long Video",
            keywords = listOf(
                "long video", "full video", "full episode",
                "full movie", "watch till end", "complete video"
            )
        ),
        // --------------------------------
        // MILESTONE 7V: SUBCATEGORIES
        // --------------------------------
        //
        // Leaf categories that live under a domain. They
        // refine a domain-level prediction into a specific
        // subcategory (cricket under sports, school under
        // education, product_promotion under advertising).
        // Keywords are the topic-specific vocabulary so a
        // strong leaf match can out-score the generic
        // domain keywords.
        // --------------------------------
        CategorySpec(
            key = "cricket",
            displayName = "Cricket",
            keywords = listOf(
                "cricket", "ipl", "wicket", "batsman",
                "bowler", "innings", "toss", "bcci",
                "century", "run out", "world cup"
            )
        ),
        CategorySpec(
            key = "football",
            displayName = "Football",
            keywords = listOf(
                "football", "soccer", "fifa", "premier league",
                "striker", "goalkeeper", "penalty", "euro",
                "world cup"
            )
        ),
        CategorySpec(
            key = "basketball",
            displayName = "Basketball",
            keywords = listOf(
                "basketball", "nba", "dunk", "hoop",
                "three pointer", "free throw", "lebron"
            )
        ),
        CategorySpec(
            key = "tennis",
            displayName = "Tennis",
            keywords = listOf(
                "tennis", "grand slam", "wimbledon", "serve",
                "set point", "nadal", "djokovic", "federer"
            )
        ),
        CategorySpec(
            key = "other_sport",
            displayName = "Other Sport",
            keywords = listOf(
                "boxing", "mma", "ufc", "formula",
                "grand prix", "olympics", "marathon"
            )
        ),
        CategorySpec(
            key = "school",
            displayName = "School",
            keywords = listOf(
                "school", "classroom", "teacher", "students",
                "homework", "principal", "school life", "exam"
            )
        ),
        CategorySpec(
            key = "university",
            displayName = "University",
            keywords = listOf(
                "university", "college", "campus", "degree",
                "professor", "admission", "hostel", "semester"
            )
        ),
        CategorySpec(
            key = "product_promotion",
            displayName = "Product Promotion",
            keywords = listOf(
                "product promotion", "promotional offer",
                "product launch", "launch offer",
                "buy 1 get 1", "limited stock"
            )
        ),
        CategorySpec(
            key = "unknown",
            displayName = "Unknown",
            keywords = emptyList()
        )
    )

    private val BY_KEY: Map<String, CategorySpec> =
        SPECS.associateBy { it.key }

    private val KEYWORDS: Map<String, List<String>> =
        SPECS.associate { it.key to it.keywords }

    /*
     * All canonical category keys.
     */
    val keys: List<String> =
        SPECS.map { it.key }

    /*
     * Category key -> keyword list, used by the
     * heuristic classifier.
     */
    val keywordMap: Map<String, List<String>> =
        KEYWORDS

    /*
     * Category key -> human readable name.
     */
    fun displayName(
        key: String?
    ): String {
        return key?.let {
            BY_KEY[it]?.displayName
        } ?: key ?: "Unknown"
    }

    /*
     * Normalize arbitrary input (case, display names,
     * aliases) to the canonical category key.
     *
     * Returns null when the input does not match any
     * known category.
     */
    fun normalize(
        input: String?
    ): String? {

        if (input.isNullOrBlank()) {
            return null
        }

        val lower =
            input.trim().lowercase()

        BY_KEY[lower]?.let {
            return it.key
        }

        ALIASES[lower]?.let {
            return it
        }

        return null
    }

    /*
     * Common variants / display-name forms that map onto
     * a canonical key. Keys themselves resolve first, so
     * an alias never shadows a real category (e.g.
     * "health" is now its own category, not a fitness
     * alias, and "cooking" its own category, not food).
     */
    private val ALIASES: Map<String, String> = mapOf(
        "sport" to "sports",
        "tech" to "technology",
        "food & cooking" to "food",
        "food and cooking" to "food",
        "finance & investing" to "finance",
        "investing" to "finance",
        "lifestyle & vlog" to "lifestyle",
        "self improvement" to "self_improvement",
        "motivational speech" to "motivational_speech",
        "movie clip" to "movie_clip",
        "movies" to "movie_clip",
        "series clip" to "series_clip",
        "tv series" to "series_clip",
        "music video" to "music_video",
        "creator edit" to "creator_edit",
        "youtuber edit" to "youtuber_edit",
        "product review" to "product_review",
        "sponsored content" to "sponsored_content",
        "influencer content" to "influencer_content",
        "live stream" to "live_stream",
        "live streaming" to "live_stream",
        "short video" to "short_video",
        "long video" to "long_video",
        "top list" to "top_list",
        "top 10" to "ranking",
        "top 5" to "ranking",
        "how to" to "how_to",
        "other" to "other"
    )

    // --------------------------------
    // MILESTONE 7V: HIERARCHY
    // --------------------------------
    //
    // child -> parent. A category with children is a
    // DOMAIN; a category that appears as a child is a
    // SUBCATEGORY. Domains may be virtual (e.g.
    // "advertising" is not itself a classifyable
    // category - only its children are).
    //
    //   ENTERTAINMENT -> MOVIE_CLIP, SERIES_CLIP, MEME,
    //                    COMEDY, MUSIC, CREATOR_EDIT
    //   SPORTS        -> CRICKET, FOOTBALL, BASKETBALL,
    //                    TENNIS, OTHER_SPORT
    //   EDUCATION     -> SCHOOL, UNIVERSITY, TUTORIAL,
    //                    SCIENCE, TECHNOLOGY
    //   MOTIVATION    -> MOTIVATIONAL_SPEECH,
    //                    SELF_IMPROVEMENT, PRODUCTIVITY
    //   ADVERTISING   -> ADVERTISEMENT, SPONSORED_CONTENT,
    //                    PRODUCT_PROMOTION
    //

    private val HIERARCHY: Map<String, String> = mapOf(
        "comedy" to "entertainment",
        "meme" to "entertainment",
        "movie_clip" to "entertainment",
        "series_clip" to "entertainment",
        "music" to "entertainment",
        "music_video" to "entertainment",
        "edit" to "entertainment",
        "creator_edit" to "entertainment",
        "youtuber_edit" to "entertainment",
        "cricket" to "sports",
        "football" to "sports",
        "basketball" to "sports",
        "tennis" to "sports",
        "other_sport" to "sports",
        "school" to "education",
        "university" to "education",
        "tutorial" to "education",
        "science" to "education",
        "technology" to "education",
        "motivational_speech" to "motivation",
        "self_improvement" to "motivation",
        "productivity" to "motivation",
        "advertisement" to "advertising",
        "sponsored_content" to "advertising",
        "product_review" to "advertising",
        "product_promotion" to "advertising"
    )

    /*
     * Immediate parent of a category, or null when the
     * category is a top-level domain (or unknown).
     */
    fun parentOf(
        key: String?
    ): String? {
        return key?.let {
            HIERARCHY[it]
        }
    }

    /*
     * The root ancestor of a category. Top-level
     * categories are their own domain.
     */
    fun domainOf(
        key: String?
    ): String? {

        if (key == null) {
            return null
        }

        var current = key

        while (true) {
            val parent = HIERARCHY[current] ?: return current
            current = parent
        }
    }

    /*
     * Direct subcategories of a domain, in catalog order.
     */
    fun childrenOf(
        domain: String
    ): List<String> {

        return keys.filter { key ->
            HIERARCHY[key] == domain
        }
    }

    /*
     * True when the category is a domain (has children).
     * Only domains are refined down to a subcategory.
     */
    fun isDomain(
        key: String?
    ): Boolean {

        return key != null &&
                childrenOf(key).isNotEmpty()
    }

    /*
     * All domains (categories or virtual parents that own
     * subcategories), in first-appearance order.
     */
    val domains: List<String> =
        buildList {
            HIERARCHY.values.forEach { parent ->
                if (parent !in this) {
                    add(parent)
                }
            }
        }
}
