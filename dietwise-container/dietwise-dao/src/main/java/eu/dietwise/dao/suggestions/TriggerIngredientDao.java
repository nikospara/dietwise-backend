package eu.dietwise.dao.suggestions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import io.smallrye.mutiny.Uni;

public interface TriggerIngredientDao {
	Uni<List<TriggerIngredient>> findAll(ReactivePersistenceContext em);
}
