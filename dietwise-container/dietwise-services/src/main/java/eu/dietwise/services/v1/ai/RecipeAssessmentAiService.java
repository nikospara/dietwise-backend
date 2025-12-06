package eu.dietwise.services.v1.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface RecipeAssessmentAiService {
	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/extract-recipe-from-html.md")
	@UserMessage("{html}")
	String extractRecipeFromHtml(String html);

	@SystemMessage(fromResource = "eu/dietwise/services/v1/ai/extract-recipe-from-markdown.md")
	@UserMessage("{markdown}")
	String extractRecipeFromMarkdown(String markdown);
}
