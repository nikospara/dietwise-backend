package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableRecipe.Builder.class)
public class RecipeMixin {
}
