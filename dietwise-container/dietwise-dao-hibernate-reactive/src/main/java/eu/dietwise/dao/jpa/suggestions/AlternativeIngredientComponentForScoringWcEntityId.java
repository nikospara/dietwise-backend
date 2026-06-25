package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AlternativeIngredientComponentForScoringWcEntityId implements Serializable {
	private UUID alternativeIngredientId;
	private UUID recommendationId;

	public AlternativeIngredientComponentForScoringWcEntityId() {
	}

	public AlternativeIngredientComponentForScoringWcEntityId(UUID alternativeIngredientId, UUID recommendationId) {
		this.alternativeIngredientId = alternativeIngredientId;
		this.recommendationId = recommendationId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlternativeIngredientComponentForScoringWcEntityId that)) return false;
		return Objects.equals(alternativeIngredientId, that.alternativeIngredientId)
				&& Objects.equals(recommendationId, that.recommendationId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(alternativeIngredientId, recommendationId);
	}
}
