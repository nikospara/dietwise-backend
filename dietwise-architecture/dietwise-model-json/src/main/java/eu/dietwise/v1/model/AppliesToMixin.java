package eu.dietwise.v1.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = AppliesTo.AppliesToIngredient.class, name = "INGREDIENT"),
		@JsonSubTypes.Type(value = AppliesTo.AppliesToRecipe.class, name = "RECIPE")
})
public class AppliesToMixin {
}
