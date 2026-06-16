package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class TriggerIngredientTranslationWcEntityId implements Serializable {
	private UUID triggerIngredientId;
	private RecipeLanguage lang;

	public TriggerIngredientTranslationWcEntityId() {
	}

	public TriggerIngredientTranslationWcEntityId(UUID triggerIngredientId, RecipeLanguage lang) {
		this.triggerIngredientId = triggerIngredientId;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TriggerIngredientTranslationWcEntityId that)) return false;
		return Objects.equals(triggerIngredientId, that.triggerIngredientId) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(triggerIngredientId, lang);
	}
}
