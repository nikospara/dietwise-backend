package eu.dietwise.services.v1.types;

/**
 * The effective editable details of one shared AlternativeIngredient, to pre-fill its edit dialog: its English name and
 * LLM explanation (published master overlaid by any Staged Change), the Working Copy version a subsequent edit must be
 * based on ({@code 0} when no Staged Change exists yet), whether a published master baseline exists behind these details
 * (so a Staged Change can be reverted to it), and its blast radius — the number of Suggestion Templates, across all
 * Rules, that reference this AlternativeIngredient and would therefore see the edit.
 */
public record AlternativeIngredientForEdit(
		String name,
		String explanationForLlm,
		long version,
		boolean published,
		long referenceCount
) {
}
