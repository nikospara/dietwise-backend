package eu.dietwise.dao.jpa.recommendations;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class RecommendationTranslationEntityId implements Serializable {
	private UUID recommendation;
	private RecipeLanguage lang;

	public RecommendationTranslationEntityId() {
	}

	public RecommendationTranslationEntityId(UUID recommendation, RecipeLanguage lang) {
		this.recommendation = recommendation;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RecommendationTranslationEntityId that)) return false;
		return Objects.equals(recommendation, that.recommendation) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(recommendation, lang);
	}
}
