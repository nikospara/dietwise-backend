package eu.dietwise.services.v1.scoring;

import java.util.Set;

import io.smallrye.mutiny.Uni;

public interface ScoringAiFacade {
	Uni<Set<String>> matchIngredientsWithRecommendations(String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe);
}
