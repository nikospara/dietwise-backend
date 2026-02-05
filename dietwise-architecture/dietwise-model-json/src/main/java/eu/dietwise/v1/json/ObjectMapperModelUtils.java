package eu.dietwise.v1.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.AppliesToMixin;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.PersonalInfoBuilderMixin;
import eu.dietwise.v1.model.PersonalInfoMixin;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeAssessmentParamBuilderMixin;
import eu.dietwise.v1.model.RecipeAssessmentParamMixin;
import eu.dietwise.v1.model.RecipeBuilderMixin;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParamBuilderMixin;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParamMixin;
import eu.dietwise.v1.model.RecipeMixin;

public interface ObjectMapperModelUtils {
	static ObjectMapper applyDefaultObjectMapperConfiguration(ObjectMapper om) {
		// Let's keep them ordered alphabetically for sanity - but keep the ImmutableXxx.Builder mixins under the classes they build
		om.addMixIn(AppliesTo.class, AppliesToMixin.class);
		om.addMixIn(PersonalInfo.class, PersonalInfoMixin.class);
		om.addMixIn(ImmutablePersonalInfo.Builder.class, PersonalInfoBuilderMixin.class);
		om.addMixIn(Recipe.class, RecipeMixin.class);
		om.addMixIn(ImmutableRecipe.Builder.class, RecipeBuilderMixin.class);
		om.addMixIn(RecipeAssessmentParam.class, RecipeAssessmentParamMixin.class);
		om.addMixIn(ImmutableRecipeAssessmentParam.Builder.class, RecipeAssessmentParamBuilderMixin.class);
		om.addMixIn(RecipeExtractionAndAssessmentParam.class, RecipeExtractionAndAssessmentParamMixin.class);
		om.addMixIn(ImmutableRecipeExtractionAndAssessmentParam.Builder.class, RecipeExtractionAndAssessmentParamBuilderMixin.class);
		return om;
	}
}
