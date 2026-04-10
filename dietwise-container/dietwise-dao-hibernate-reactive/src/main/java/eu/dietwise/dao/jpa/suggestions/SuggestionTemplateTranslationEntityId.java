package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class SuggestionTemplateTranslationEntityId implements Serializable {
	private UUID suggestionTemplate;
	private RecipeLanguage lang;

	public SuggestionTemplateTranslationEntityId() {
	}

	public SuggestionTemplateTranslationEntityId(UUID suggestionTemplate, RecipeLanguage lang) {
		this.suggestionTemplate = suggestionTemplate;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SuggestionTemplateTranslationEntityId that)) return false;
		return Objects.equals(suggestionTemplate, that.suggestionTemplate) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(suggestionTemplate, lang);
	}
}
