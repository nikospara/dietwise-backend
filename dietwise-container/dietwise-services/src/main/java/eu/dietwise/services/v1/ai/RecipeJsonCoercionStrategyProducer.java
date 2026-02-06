package eu.dietwise.services.v1.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class RecipeJsonCoercionStrategyProducer {
	@Produces
	@ApplicationScoped
	public RecipeJsonCoercionStrategy recipeJsonCoercionStrategy() {
		return new KeyValuePairsCoercionStrategy();
	}
}
