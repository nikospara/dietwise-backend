package eu.dietwise.services.v1.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import eu.dietwise.v1.model.ScoringData;
import eu.dietwise.v1.model.Suggestion;

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage.class, name = "RECIPES"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage.class, name = "MORE_THAN_ONE_RECIPE"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage.class, name = "SUGGESTIONS"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.ScoringRecipeAssessmentMessage.class, name = "SCORING"),
		@JsonSubTypes.Type(value = RecipeAssessmentMessage.RecipeAssessmentErrorMessage.class, name = "ERROR")
})
public sealed interface RecipeAssessmentMessage {
	record RecipeExtractionRecipeAssessmentMessage(
			List<RecipeAndDetectionType> recipes,
			String pageText
	) implements RecipeAssessmentMessage {
	}

	record MoreThanOneRecipesAssessmentMessage(int numberOfRecipes) implements RecipeAssessmentMessage {
	}

	record SuggestionsRecipeAssessmentMessage(List<Suggestion> suggestions) implements RecipeAssessmentMessage {
	}

	record ScoringRecipeAssessmentMessage(ScoringData scoringData) implements RecipeAssessmentMessage {
	}

	record RecipeAssessmentErrorMessage(List<String> errors) implements RecipeAssessmentMessage {
	}
}
