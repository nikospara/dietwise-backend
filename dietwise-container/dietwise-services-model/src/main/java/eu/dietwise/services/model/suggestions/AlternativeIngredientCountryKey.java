package eu.dietwise.services.model.suggestions;

import java.util.UUID;

import eu.dietwise.v1.types.Country;

/**
 * Identifies one seasonality or cost cell: the Alternative Ingredient and the country it applies to. Used as a map key
 * when overlaying published master seasonality/cost with the Working Copy.
 */
public record AlternativeIngredientCountryKey(UUID alternativeIngredientId, Country country) {
}
