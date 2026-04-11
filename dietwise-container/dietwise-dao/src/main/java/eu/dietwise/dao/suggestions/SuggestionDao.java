package eu.dietwise.dao.suggestions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.HasRuleId;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface SuggestionDao {
	Uni<List<Suggestion>> retrieveByRule(ReactivePersistenceContext em, HasRuleId ruleId, Country country, Ingredient ingredient, RecipeLanguage lang);
}
