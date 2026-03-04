package eu.dietwise.services.v1.suggestions;

import jakarta.enterprise.context.RequestScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
		modelName = "roleOrTechnique2",
		chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@RequestScoped
public interface IngredientRoleAiService2 {
	@UserMessage("""
			ingredient: {ingredientNameInRecipe}
			
			instructions:
			{instructionsAsMarkdownList}
			
			Select the roleOrTechnique value.
			Output only the value.""")
	String assessIngredientRole(String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList);
}
