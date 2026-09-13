package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Transformation rule for privacy region types.
 *
 * A rule maps a PrivacyRegionType to a PrivacyTransformation.
 * Rules carry a priority; when several rules could match a
 * region (e.g. an overlapping region list), the highest-priority
 * rule wins (deterministic). Rules are immutable and versioned.
 */
data class PrivacyRule(
    val regionType: PrivacyRegionType,
    val transformation: PrivacyTransformation,
    val priority: Int = 0,
    val appliedWhenUnknownRisk: Boolean = false,
    val rationale: String = ""
)

/*
 * Milestone 8B-13.
 *
 * Deterministic table of transformation rules for a policy mode.
 *
 * Declarative precedence:
 *   - transformationFor() returns the rule with the highest
 *     priority whose region type matches (ties: first in list
 *     order).
 *   - a rule that is NOT flagged for unknown-risk use does not
 *     fire for UNKNOWN_SENSITIVE_REGION regions produced under
 *     OCR-unavailable conditions.
 *
 * The default tables are the documented behaviour of the three
 * policy modes (spec §16):
 *
 *   RESEARCH : preserve research utility; blur UI chrome,
 *              mask/fill private identifiers and text. No drops.
 *   BALANCED : same regions, slightly weaker treatment for
 *              private text (blur instead of mask) so more
 *              evidence survives, still never raw identifiers.
 *   STRICT   : mask everything, crop UI bands, and allow
 *              DROP_FRAME when risk is high and value low.
 */
class PrivacyRuleTable private constructor(
    private val rules: List<PrivacyRule>
) {

    init {
        require(rules.isNotEmpty()) {
            "a privacy rule table must contain at least one rule"
        }
    }

    fun transformationFor(
        regionType: PrivacyRegionType,
        unknownRisk: Boolean = false
    ): PrivacyTransformation {
        // Max priority wins; ties resolve to the first rule in
        // list order, keeping everything deterministic.
        return ruleFor(regionType, unknownRisk)
            ?.transformation
            ?: PrivacyTransformation.NONE
    }

    fun ruleFor(
        regionType: PrivacyRegionType,
        unknownRisk: Boolean = false
    ): PrivacyRule? {
        return rules
            .filter { it.regionType == regionType }
            .filter { !unknownRisk || it.appliedWhenUnknownRisk }
            .maxByOrNull { it.priority }
    }

    fun allRules(): List<PrivacyRule> = rules.toList()

    companion object {

        val DEFAULT = forPolicyMode(PrivacyPolicyMode.RESEARCH)

        fun forPolicyMode(
            mode: PrivacyPolicyMode
        ): PrivacyRuleTable {
            return when (mode) {
                PrivacyPolicyMode.RESEARCH ->
                    PrivacyRuleTable(
                        listOf(
                            PrivacyRule(
                                PrivacyRegionType.SYSTEM_UI,
                                PrivacyTransformation.BLUR,
                                priority = 1,
                                rationale = "blur system chrome, keep layout"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.NOTIFICATION,
                                PrivacyTransformation.BLUR,
                                priority = 2,
                                rationale = "blur notification previews"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PRIVATE_TEXT,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask chat/private text"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PERSONAL_IDENTIFIER,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask identifiers"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PERSONAL_IMAGE,
                                PrivacyTransformation.BLUR,
                                priority = 2,
                                rationale = "blur personal images"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.SENSITIVE_APPLICATION_UI,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask sensitive app UI"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.LOCATION_INFORMATION,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask location content"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                                PrivacyTransformation.BLUR,
                                priority = 0,
                                appliedWhenUnknownRisk = true,
                                rationale = "blur, never claim safe"
                            )
                        )
                    )

                PrivacyPolicyMode.BALANCED ->
                    PrivacyRuleTable(
                        listOf(
                            PrivacyRule(
                                PrivacyRegionType.SYSTEM_UI,
                                PrivacyTransformation.BLUR,
                                priority = 1,
                                rationale = "blur system chrome"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.NOTIFICATION,
                                PrivacyTransformation.BLUR,
                                priority = 2,
                                rationale = "blur notification previews"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PRIVATE_TEXT,
                                PrivacyTransformation.BLUR,
                                priority = 2,
                                rationale = "blur, keep some evidence"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PERSONAL_IDENTIFIER,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask identifiers"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PERSONAL_IMAGE,
                                PrivacyTransformation.BLUR,
                                priority = 2,
                                rationale = "blur personal images"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.SENSITIVE_APPLICATION_UI,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask sensitive app UI"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.LOCATION_INFORMATION,
                                PrivacyTransformation.PIXELATE,
                                priority = 2,
                                rationale = "pixelate location markers"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                                PrivacyTransformation.BLUR,
                                priority = 0,
                                appliedWhenUnknownRisk = true,
                                rationale = "blur, never claim safe"
                            )
                        )
                    )

                PrivacyPolicyMode.STRICT ->
                    PrivacyRuleTable(
                        listOf(
                            PrivacyRule(
                                PrivacyRegionType.SYSTEM_UI,
                                PrivacyTransformation.MASK,
                                priority = 2,
                                rationale = "mask system chrome"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.NOTIFICATION,
                                PrivacyTransformation.MASK,
                                priority = 3,
                                rationale = "mask notifications"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PRIVATE_TEXT,
                                PrivacyTransformation.MASK,
                                priority = 4,
                                rationale = "mask private text"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PERSONAL_IDENTIFIER,
                                PrivacyTransformation.MASK,
                                priority = 4,
                                rationale = "mask identifiers"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.PERSONAL_IMAGE,
                                PrivacyTransformation.MASK,
                                priority = 4,
                                rationale = "mask personal images"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.SENSITIVE_APPLICATION_UI,
                                PrivacyTransformation.MASK,
                                priority = 4,
                                rationale = "mask sensitive app UI"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.LOCATION_INFORMATION,
                                PrivacyTransformation.MASK,
                                priority = 4,
                                rationale = "mask location content"
                            ),
                            PrivacyRule(
                                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                                PrivacyTransformation.MASK,
                                priority = 4,
                                appliedWhenUnknownRisk = true,
                                rationale = "mask unknown risk"
                            )
                        )
                    )
            }
        }

        fun custom(rules: List<PrivacyRule>): PrivacyRuleTable =
            PrivacyRuleTable(rules)
    }
}