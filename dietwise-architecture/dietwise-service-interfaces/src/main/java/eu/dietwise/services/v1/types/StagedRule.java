package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * A Rule as shown in the backoffice grid: its effective values (published master overlaid by any Staged Change), the
 * ids of the shared reference entities it points at, which of its cells carry a pending change, the Rule's own change
 * state relative to master, the completeness of its rationale translations, and the Working Copy version a subsequent
 * edit must be based on.
 *
 * <p>{@code changeState} reflects the Rule's <em>own</em> Staged Changes (rationale, active); {@code changedFields}
 * additionally flags a Trigger Ingredient or Role or Technique cell when that shared entity has a pending edit, so the
 * blast radius of an edit shows on every referencing Rule even when the Rule itself is unchanged. {@code
 * rationaleTranslations} carries, for each non-English language, whether the rationale is translated, missing, or has a
 * pending translation change.
 */
public record StagedRule(
		Rule rule,
		UUID triggerIngredientId,
		UUID roleOrTechniqueId,
		RuleChangeState changeState,
		Set<RuleField> changedFields,
		Map<RecipeLanguage, TranslationState> rationaleTranslations,
		long version
) {
}
