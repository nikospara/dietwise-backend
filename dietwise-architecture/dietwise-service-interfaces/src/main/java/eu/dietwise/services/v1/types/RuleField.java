package eu.dietwise.services.v1.types;

/**
 * A field of a Rule shown in the backoffice grid that can carry a pending change. Used to highlight each grid cell
 * independently: {@link #RATIONALE} and {@link #ACTIVE} are the Rule's own Staged Changes, while
 * {@link #TRIGGER_INGREDIENT} and {@link #ROLE_OR_TECHNIQUE} mean the shared reference entity shown in that cell has a
 * pending edit, so every Rule referencing it is flagged. {@link #SUGGESTION_TEMPLATES} means at least one of the Rule's
 * Suggestion Templates has a Staged Change; it lights the Suggestions affordance without making the Rule row pending.
 */
public enum RuleField {
	RATIONALE,
	ACTIVE,
	TRIGGER_INGREDIENT,
	ROLE_OR_TECHNIQUE,
	SUGGESTION_TEMPLATES
}
