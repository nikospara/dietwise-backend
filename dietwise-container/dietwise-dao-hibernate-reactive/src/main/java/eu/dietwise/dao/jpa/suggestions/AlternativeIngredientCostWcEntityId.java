package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.Country;

public class AlternativeIngredientCostWcEntityId implements Serializable {
	private UUID alternativeIngredientId;
	private String countryCode2;

	public AlternativeIngredientCostWcEntityId() {
	}

	public AlternativeIngredientCostWcEntityId(UUID alternativeIngredientId, Country country) {
		this.alternativeIngredientId = alternativeIngredientId;
		this.countryCode2 = country == null ? null : country.getCode2();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlternativeIngredientCostWcEntityId that)) return false;
		return Objects.equals(alternativeIngredientId, that.alternativeIngredientId) && Objects.equals(countryCode2, that.countryCode2);
	}

	@Override
	public int hashCode() {
		return Objects.hash(alternativeIngredientId, countryCode2);
	}
}
