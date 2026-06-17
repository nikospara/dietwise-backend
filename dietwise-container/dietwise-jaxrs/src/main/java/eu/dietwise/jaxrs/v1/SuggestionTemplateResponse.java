package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.StagedSuggestionTemplate;

/**
 * One of a Rule's Suggestion Templates as shown in the backoffice panel: its id, the English name of the
 * AlternativeIngredient it suggests, its effective English {@code restriction}, {@code equivalence} and {@code
 * techniqueNotes} (published master overlaid by any Staged Change, any of which may be {@code null}), the names of the
 * fields that carry a pending change, the per-field per-language translation completeness (field name -&gt; language
 * name -&gt; state name), its effective {@code active} state (a deactivated template is skipped by recipe assessment),
 * whether that active state is a pending change ({@code activeChanged}), whether it has a published master baseline
 * ({@code published} is {@code false} for a Working-Copy-only template that can be discarded), and the Working Copy
 * version a subsequent edit must be based on. Templates are returned in their {@code alternative_order}.
 */
public record SuggestionTemplateResponse(
		String id,
		String alternativeIngredientName,
		String restriction,
		String equivalence,
		String techniqueNotes,
		List<String> changedFields,
		Map<String, Map<String, String>> translations,
		boolean active,
		boolean activeChanged,
		boolean published,
		long version
) {
	public static SuggestionTemplateResponse from(StagedSuggestionTemplate staged) {
		var template = staged.template();
		return new SuggestionTemplateResponse(
				template.getId().asString(),
				template.getAlternative().asString(),
				template.getRestriction().orElse(null),
				template.getEquivalence().orElse(null),
				template.getTechniqueNotes().orElse(null),
				staged.changedFields().stream().map(Enum::name).toList(),
				toTranslationNames(staged),
				staged.active(),
				staged.activeChanged(),
				staged.published(),
				staged.version()
		);
	}

	private static Map<String, Map<String, String>> toTranslationNames(StagedSuggestionTemplate staged) {
		return staged.translations().entrySet().stream().collect(Collectors.toMap(
				field -> field.getKey().name(),
				field -> field.getValue().entrySet().stream().collect(Collectors.toMap(
						lang -> lang.getKey().name(),
						lang -> lang.getValue().name()))));
	}

	public static List<SuggestionTemplateResponse> fromAll(List<StagedSuggestionTemplate> templates) {
		return templates.stream().map(SuggestionTemplateResponse::from).toList();
	}
}
