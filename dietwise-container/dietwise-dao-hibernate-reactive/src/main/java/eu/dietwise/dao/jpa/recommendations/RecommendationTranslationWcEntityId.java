package eu.dietwise.dao.jpa.recommendations;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class RecommendationTranslationWcEntityId implements Serializable {
	private UUID recommendationId;
	private RecipeLanguage lang;

	public RecommendationTranslationWcEntityId() {
	}

	public RecommendationTranslationWcEntityId(UUID recommendationId, RecipeLanguage lang) {
		this.recommendationId = recommendationId;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RecommendationTranslationWcEntityId that)) return false;
		return Objects.equals(recommendationId, that.recommendationId) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(recommendationId, lang);
	}
}
