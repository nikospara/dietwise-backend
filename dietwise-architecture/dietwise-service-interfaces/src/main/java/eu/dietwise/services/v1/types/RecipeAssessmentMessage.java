package eu.dietwise.services.v1.types;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import eu.dietwise.v1.model.ScoringData;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateId;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.SuggestionTemplateId;

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

	record SuggestionsRecipeAssessmentMessage(
			List<Suggestion> suggestions) implements RecipeAssessmentMessage, HasSuggestionTemplateIds {
		@Override
		public Set<SuggestionTemplateId> getSuggestionTemplateIds() {
			return suggestions.stream().map(HasSuggestionTemplateId::getId).collect(Collectors.toSet());
		}
	}

	record ScoringRecipeAssessmentMessage(ScoringData scoringData) implements RecipeAssessmentMessage {
	}

	record RecipeAssessmentErrorMessage(List<String> errors) implements RecipeAssessmentMessage {
	}
}
