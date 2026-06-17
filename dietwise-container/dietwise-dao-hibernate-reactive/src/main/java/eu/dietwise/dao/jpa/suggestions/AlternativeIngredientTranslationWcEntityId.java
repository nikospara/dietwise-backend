package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class AlternativeIngredientTranslationWcEntityId implements Serializable {
	private UUID alternativeIngredientId;
	private RecipeLanguage lang;

	public AlternativeIngredientTranslationWcEntityId() {
	}

	public AlternativeIngredientTranslationWcEntityId(UUID alternativeIngredientId, RecipeLanguage lang) {
		this.alternativeIngredientId = alternativeIngredientId;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlternativeIngredientTranslationWcEntityId that)) return false;
		return Objects.equals(alternativeIngredientId, that.alternativeIngredientId) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(alternativeIngredientId, lang);
	}
}
