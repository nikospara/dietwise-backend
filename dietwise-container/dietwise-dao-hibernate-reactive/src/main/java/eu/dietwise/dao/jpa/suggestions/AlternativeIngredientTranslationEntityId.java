package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class AlternativeIngredientTranslationEntityId implements Serializable {
	private UUID alternativeIngredient;
	private RecipeLanguage lang;

	public AlternativeIngredientTranslationEntityId() {
	}

	public AlternativeIngredientTranslationEntityId(UUID alternativeIngredient, RecipeLanguage lang) {
		this.alternativeIngredient = alternativeIngredient;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlternativeIngredientTranslationEntityId that)) return false;
		return Objects.equals(alternativeIngredient, that.alternativeIngredient) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(alternativeIngredient, lang);
	}
}
