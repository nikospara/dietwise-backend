package eu.dietwise.services.v1.extraction.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;

@ApplicationScoped
public class RecipeJsonCoercionStrategyProducer {
	@Produces
	@ApplicationScoped
	public RecipeJsonCoercionStrategy recipeJsonCoercionStrategy() {
		return new KeyValuePairsCoercionStrategy();
	}
}
