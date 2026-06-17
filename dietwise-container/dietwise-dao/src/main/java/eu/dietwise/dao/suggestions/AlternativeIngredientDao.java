package eu.dietwise.dao.suggestions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface AlternativeIngredientDao {
	Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * The published AlternativeIngredients as pickable options (id and English name), sorted by name. Backs the
	 * filtering combobox an editor uses to add a Suggestion Template to a Rule.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);
}
