package eu.dietwise.services.v1.impl;

import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.v1.model.Recipe;

/**
 * The outcome of recipe extraction, classified by how many recipes were found. The assessment pipeline
 * branches on this: a single recipe is assessed, multiple recipes ask the user to choose which one, and
 * none yields an error message.
 */
sealed interface RecipeExtractionOutcome {
	record NoRecipes() implements RecipeExtractionOutcome {}

	record SingleRecipe(Recipe recipe) implements RecipeExtractionOutcome {}

	record MultipleRecipes(int count) implements RecipeExtractionOutcome {}

	static RecipeExtractionOutcome classify(RecipeExtractionRecipeAssessmentMessage extraction) {
		var recipes = extraction.recipes();
		if (recipes == null || recipes.isEmpty()) {
			return new NoRecipes();
		} else if (recipes.size() == 1) {
			return new SingleRecipe(recipes.getFirst().recipe());
		} else {
			return new MultipleRecipes(recipes.size());
		}
	}
}
