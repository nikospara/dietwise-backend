package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableRecipeAssessmentParam.Builder.class)
public class RecipeAssessmentParamMixin {
}
