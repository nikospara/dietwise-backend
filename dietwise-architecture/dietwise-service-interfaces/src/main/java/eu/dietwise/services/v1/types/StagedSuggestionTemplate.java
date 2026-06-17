package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * A Rule's Suggestion Template as shown in the backoffice panel: its effective values (published master overlaid by any
 * Staged Change), the id of the AlternativeIngredient it suggests, which of its English text fields carry a pending
 * change, the per-field per-language completeness of its translations, the per-language completeness of its shared
 * AlternativeIngredient's translations, its effective active state, whether that active state is a pending change,
 * whether it has a published master baseline, and the Working Copy version a subsequent edit must be based on ({@code 0}
 * when the template has no Staged Change). The template's effective AlternativeIngredient name reflects any staged edit
 * of the shared entity. {@code translations} holds, for each translatable field, the {@link TranslationState} of every
 * non-English language; {@code alternativeIngredientTranslations} holds the same for the shared AlternativeIngredient's
 * name and explanation. A deactivated template ({@code active} false) is skipped by recipe assessment; {@code
 * activeChanged} is {@code true} when the effective active state differs from published master because of a staged
 * Deactivate or Activate. {@code published} is {@code false} for a Working-Copy-only template an editor added but has not
 * published — such a template can be discarded (rather than deactivated).
 */
public record StagedSuggestionTemplate(
		SuggestionTemplate template,
		UUID alternativeIngredientId,
		Set<SuggestionTemplateField> changedFields,
		Map<SuggestionTemplateField, Map<RecipeLanguage, TranslationState>> translations,
		Map<RecipeLanguage, TranslationState> alternativeIngredientTranslations,
		boolean active,
		boolean activeChanged,
		boolean published,
		long version
) {
}
