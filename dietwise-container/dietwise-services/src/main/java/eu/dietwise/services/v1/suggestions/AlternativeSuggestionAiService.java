package eu.dietwise.services.v1.suggestions;

public interface AlternativeSuggestionAiService {
	String suggestAlternatives(String availableAlternativesAsMarkdownList, String recipeName, String ingredientNameInRecipe, String ingredientRoleOrTechnique);
}
