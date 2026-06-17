package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class SuggestionTemplateTranslationWcEntityId implements Serializable {
	private UUID suggestionTemplateId;
	private RecipeLanguage lang;

	public SuggestionTemplateTranslationWcEntityId() {
	}

	public SuggestionTemplateTranslationWcEntityId(UUID suggestionTemplateId, RecipeLanguage lang) {
		this.suggestionTemplateId = suggestionTemplateId;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SuggestionTemplateTranslationWcEntityId that)) return false;
		return Objects.equals(suggestionTemplateId, that.suggestionTemplateId) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(suggestionTemplateId, lang);
	}
}
