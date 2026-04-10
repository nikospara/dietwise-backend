package eu.dietwise.dao.jpa.suggestions;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

public class RoleOrTechniqueTranslationEntityId implements Serializable {
	private UUID roleOrTechnique;
	private RecipeLanguage lang;

	public RoleOrTechniqueTranslationEntityId() {
	}

	public RoleOrTechniqueTranslationEntityId(UUID roleOrTechnique, RecipeLanguage lang) {
		this.roleOrTechnique = roleOrTechnique;
		this.lang = lang;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RoleOrTechniqueTranslationEntityId that)) return false;
		return Objects.equals(roleOrTechnique, that.roleOrTechnique) && lang == that.lang;
	}

	@Override
	public int hashCode() {
		return Objects.hash(roleOrTechnique, lang);
	}
}
