package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface AlternativeIngredientDao {
	Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * The selectable AlternativeIngredients for the backoffice: published master overlaid by the Working Copy (mirror
	 * wins), including Working-Copy-only entries, as id and English name, sorted by name. Backs the filtering combobox an
	 * editor uses to add a Suggestion Template to a Rule.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);

	/**
	 * Stage a brand-new AlternativeIngredient in the Working Copy with the given name and no explanation. Uniqueness of
	 * the name across master and the Working Copy is the caller's responsibility; the Working Copy carries a unique-name
	 * constraint as a backstop.
	 *
	 * @return The generated Working Copy id of the new AlternativeIngredient
	 */
	Uni<UUID> createAlternativeIngredient(ReactivePersistenceTxContext tx, String name);
}
