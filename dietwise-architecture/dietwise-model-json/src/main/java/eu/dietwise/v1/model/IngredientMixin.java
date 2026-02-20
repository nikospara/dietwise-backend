package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableIngredient.Builder.class)
public abstract class IngredientMixin {
}
