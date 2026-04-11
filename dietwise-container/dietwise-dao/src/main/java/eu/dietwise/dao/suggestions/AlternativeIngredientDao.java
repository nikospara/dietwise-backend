package eu.dietwise.dao.suggestions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface AlternativeIngredientDao {
	Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);
}
