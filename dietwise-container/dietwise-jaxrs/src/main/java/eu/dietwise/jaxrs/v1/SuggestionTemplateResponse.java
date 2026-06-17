package eu.dietwise.jaxrs.v1;

import java.util.List;

import eu.dietwise.v1.model.SuggestionTemplate;

/**
 * One of a Rule's Suggestion Templates as shown in the backoffice panel: its id, the English name of the
 * AlternativeIngredient it suggests, and its English {@code restriction}, {@code equivalence} and {@code techniqueNotes},
 * any of which may be {@code null}. Templates are returned in their {@code alternative_order}.
 */
public record SuggestionTemplateResponse(
		String id,
		String alternativeIngredientName,
		String restriction,
		String equivalence,
		String techniqueNotes
) {
	public static SuggestionTemplateResponse from(SuggestionTemplate template) {
		return new SuggestionTemplateResponse(
				template.getId().asString(),
				template.getAlternative().asString(),
				template.getRestriction().orElse(null),
				template.getEquivalence().orElse(null),
				template.getTechniqueNotes().orElse(null)
		);
	}

	public static List<SuggestionTemplateResponse> fromAll(List<SuggestionTemplate> templates) {
		return templates.stream().map(SuggestionTemplateResponse::from).toList();
	}
}
