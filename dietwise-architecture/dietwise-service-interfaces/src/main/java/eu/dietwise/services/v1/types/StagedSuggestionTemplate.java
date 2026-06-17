package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.Set;

import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * A Rule's Suggestion Template as shown in the backoffice panel: its effective values (published master overlaid by any
 * Staged Change), which of its English text fields carry a pending change, the per-field per-language completeness of
 * its translations, and the Working Copy version a subsequent edit must be based on ({@code 0} when the template has no
 * Staged Change). {@code translations} holds, for each translatable field, the {@link TranslationState} of every
 * non-English language.
 */
public record StagedSuggestionTemplate(
		SuggestionTemplate template,
		Set<SuggestionTemplateField> changedFields,
		Map<SuggestionTemplateField, Map<RecipeLanguage, TranslationState>> translations,
		long version
) {
}
