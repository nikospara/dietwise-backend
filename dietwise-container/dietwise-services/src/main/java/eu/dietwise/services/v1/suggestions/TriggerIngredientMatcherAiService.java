package eu.dietwise.services.v1.suggestions;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
		modelName = "triggerIngredient",
		chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@RequestScoped
public interface TriggerIngredientMatcherAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/triggerIngredient-system.md")
	@UserMessage(fromResource = "eu/dietwise/services/v1/ai/triggerIngredient-user.md")
	String matchIngredientToTrigger(String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, String ingredientRoleOrTechnique);
}
