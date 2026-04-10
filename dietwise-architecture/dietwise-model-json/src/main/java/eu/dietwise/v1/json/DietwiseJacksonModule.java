package eu.dietwise.v1.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.AppliesToMixin;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.ImmutableStatisticsParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.IngredientBuilderMixin;
import eu.dietwise.v1.model.IngredientMixin;
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
import eu.dietwise.v1.model.StatisticsParam;
import eu.dietwise.v1.model.StatisticsParamBuilderMixin;
import eu.dietwise.v1.model.StatisticsParamMixin;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.CountryDeserializer;
import eu.dietwise.v1.types.CountrySerializer;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RecipeLanguageDeserializer;
import eu.dietwise.v1.types.RecipeLanguageSerializer;
import eu.dietwise.v1.types.RepresentableAsStringKeySerializer;
import eu.dietwise.v1.types.RepresentableAsStringSerializer;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.SuggestionTemplateIdMixin;

public class DietwiseJacksonModule extends SimpleModule {
	public DietwiseJacksonModule() {
		// Let's keep them ordered alphabetically for sanity - but keep the ImmutableXxx.Builder mixins under the classes they build
		setMixInAnnotation(AppliesTo.class, AppliesToMixin.class);
		setMixInAnnotation(Ingredient.class, IngredientMixin.class);
		setMixInAnnotation(ImmutableIngredient.Builder.class, IngredientBuilderMixin.class);
		setMixInAnnotation(PersonalInfo.class, PersonalInfoMixin.class);
		setMixInAnnotation(ImmutablePersonalInfo.Builder.class, PersonalInfoBuilderMixin.class);
		setMixInAnnotation(Recipe.class, RecipeMixin.class);
		setMixInAnnotation(ImmutableRecipe.Builder.class, RecipeBuilderMixin.class);
		setMixInAnnotation(RecipeAssessmentParam.class, RecipeAssessmentParamMixin.class);
		setMixInAnnotation(ImmutableRecipeAssessmentParam.Builder.class, RecipeAssessmentParamBuilderMixin.class);
		setMixInAnnotation(RecipeExtractionAndAssessmentParam.class, RecipeExtractionAndAssessmentParamMixin.class);
		setMixInAnnotation(ImmutableRecipeExtractionAndAssessmentParam.Builder.class, RecipeExtractionAndAssessmentParamBuilderMixin.class);
		setMixInAnnotation(StatisticsParam.class, StatisticsParamMixin.class);
		setMixInAnnotation(ImmutableStatisticsParam.Builder.class, StatisticsParamBuilderMixin.class);
		setMixInAnnotation(SuggestionTemplateId.class, SuggestionTemplateIdMixin.class);

		addSerializer(Country.class, new CountrySerializer());
		addDeserializer(Country.class, new CountryDeserializer());
		addSerializer(RecipeLanguage.class, new RecipeLanguageSerializer());
		addDeserializer(RecipeLanguage.class, new RecipeLanguageDeserializer());
		addSerializer(RepresentableAsString.class, new RepresentableAsStringSerializer());
		addKeySerializer(RepresentableAsString.class, new RepresentableAsStringKeySerializer());
	}
}
