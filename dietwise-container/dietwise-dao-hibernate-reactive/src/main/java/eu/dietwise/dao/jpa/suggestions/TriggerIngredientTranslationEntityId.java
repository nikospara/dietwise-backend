package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class TriggerIngredientTranslationEntityId implements Serializable {
	private UUID triggerIngredient;
	private RecipeLanguage lang;

	public TriggerIngredientTranslationEntityId() {
	}

	public TriggerIngredientTranslationEntityId(UUID triggerIngredient, RecipeLanguage lang) {
		this.triggerIngredient = triggerIngredient;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TriggerIngredientTranslationEntityId that)) return false;
		return Objects.equals(triggerIngredient, that.triggerIngredient) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(triggerIngredient, lang);
	}
}
