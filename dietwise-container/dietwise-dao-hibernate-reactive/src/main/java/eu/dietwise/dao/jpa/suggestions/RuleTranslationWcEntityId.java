package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class RuleTranslationWcEntityId implements Serializable {
	private UUID ruleId;
	private RecipeLanguage lang;

	public RuleTranslationWcEntityId() {
	}

	public RuleTranslationWcEntityId(UUID ruleId, RecipeLanguage lang) {
		this.ruleId = ruleId;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RuleTranslationWcEntityId that)) return false;
		return Objects.equals(ruleId, that.ruleId) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(ruleId, lang);
	}
}
