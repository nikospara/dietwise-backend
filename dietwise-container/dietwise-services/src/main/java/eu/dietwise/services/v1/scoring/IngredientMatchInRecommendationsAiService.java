package eu.dietwise.services.v1.scoring;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
		modelName = "ingredientMatchInRecommendations",
		chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@RequestScoped
public interface IngredientMatchInRecommendationsAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/ingredientMatchInRecommendations-system.md")
	@UserMessage(fromResource = "eu/dietwise/services/v1/ai/ingredientMatchInRecommendations-user.md")
	String matchIngredientsWithRecommendations(String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe);
}
