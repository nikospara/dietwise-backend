package eu.dietwise.services.v1.suggestions;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
		modelName = "roleOrTechniqueNl",
		chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@RequestScoped
public interface IngredientRoleNlAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/roleOrTechnique-system.md")
	@UserMessage(fromResource = "eu/dietwise/services/v1/ai/roleOrTechnique-user.md")
	String assessIngredientRole(String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList);
}
