package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.Country;

public class AlternativeIngredientCostEntityId implements Serializable {
	private UUID alternativeIngredient;
	private String countryCode2;

	public AlternativeIngredientCostEntityId() {
	}

	public AlternativeIngredientCostEntityId(UUID alternativeIngredient, Country country) {
		this.alternativeIngredient = alternativeIngredient;
		this.countryCode2 = country == null ? null : country.getCode2();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlternativeIngredientCostEntityId that)) return false;
		return Objects.equals(alternativeIngredient, that.alternativeIngredient) && Objects.equals(countryCode2, that.countryCode2);
	}

	@Override
	public int hashCode() {
		return Objects.hash(alternativeIngredient, countryCode2);
	}
}
