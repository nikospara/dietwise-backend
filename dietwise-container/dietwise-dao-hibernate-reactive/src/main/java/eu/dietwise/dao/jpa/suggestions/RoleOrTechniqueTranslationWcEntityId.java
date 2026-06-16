package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class RoleOrTechniqueTranslationWcEntityId implements Serializable {
	private UUID roleOrTechniqueId;
	private RecipeLanguage lang;

	public RoleOrTechniqueTranslationWcEntityId() {
	}

	public RoleOrTechniqueTranslationWcEntityId(UUID roleOrTechniqueId, RecipeLanguage lang) {
		this.roleOrTechniqueId = roleOrTechniqueId;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RoleOrTechniqueTranslationWcEntityId that)) return false;
		return Objects.equals(roleOrTechniqueId, that.roleOrTechniqueId) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(roleOrTechniqueId, lang);
	}
}
