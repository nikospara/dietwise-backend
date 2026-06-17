package eu.dietwise.jaxrs.v1;

import java.util.List;

import eu.dietwise.services.v1.types.StagedSuggestionTemplate;

/**
 * One of a Rule's Suggestion Templates as shown in the backoffice panel: its id, the English name of the
 * AlternativeIngredient it suggests, its effective English {@code restriction}, {@code equivalence} and {@code
 * techniqueNotes} (published master overlaid by any Staged Change, any of which may be {@code null}), the names of the
 * fields that carry a pending change, and the Working Copy version a subsequent edit must be based on. Templates are
 * returned in their {@code alternative_order}.
 */
public record SuggestionTemplateResponse(
		String id,
		String alternativeIngredientName,
		String restriction,
		String equivalence,
		String techniqueNotes,
		List<String> changedFields,
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
				staged.version()
		);
	}

	public static List<SuggestionTemplateResponse> fromAll(List<StagedSuggestionTemplate> templates) {
		return templates.stream().map(SuggestionTemplateResponse::from).toList();
	}
}
