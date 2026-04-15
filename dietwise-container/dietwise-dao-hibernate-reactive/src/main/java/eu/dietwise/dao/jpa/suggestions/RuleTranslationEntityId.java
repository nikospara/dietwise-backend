package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class RuleTranslationEntityId implements Serializable {
	private UUID rule;
	private RecipeLanguage lang;

	public RuleTranslationEntityId() {
	}

	public RuleTranslationEntityId(UUID rule, RecipeLanguage lang) {
		this.rule = rule;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RuleTranslationEntityId that)) return false;
		return Objects.equals(rule, that.rule) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(rule, lang);
	}
}
