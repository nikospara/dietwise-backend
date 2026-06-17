package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.v1.model.SuggestionTemplate;
import io.smallrye.mutiny.Uni;

public interface SuggestionTemplateDao {
	/**
	 * The published Suggestion Templates of one Rule, each with the AlternativeIngredient it suggests and its English
	 * {@code restriction}, {@code equivalence} and {@code techniqueNotes}, ordered by {@code alternative_order}. A Rule
	 * with no templates yields an empty list.
	 */
	Uni<List<SuggestionTemplate>> findByRule(ReactivePersistenceContext em, UUID ruleId);
}
