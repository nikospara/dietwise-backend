package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.EnumType.STRING;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.RecipeLanguage;

/**
 * The Working Copy mirror of {@link RoleOrTechniqueTranslationEntity}: a proposed per-language Role or Technique name
 * and explanation translation staged for publish. Sparse — a row exists only for a translation that differs from
 * published master. The Role or Technique is stored as a raw id and resolved against published master union Working
 * Copy at read time, so this table carries no foreign keys.
 */
@Entity
@IdClass(RoleOrTechniqueTranslationWcEntityId.class)
@Table(name = "DW_ROLE_OR_TECHNIQUE_TRANSLATION_WC")
public class RoleOrTechniqueTranslationWcEntity {
	@Id
	@Column(name = "role_or_technique_id")
	private UUID roleOrTechniqueId;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "name")
	private String name;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	@Column(name = "version")
	private long version;

	public UUID getRoleOrTechniqueId() {
		return roleOrTechniqueId;
	}

	public void setRoleOrTechniqueId(UUID roleOrTechniqueId) {
		this.roleOrTechniqueId = roleOrTechniqueId;
	}

	public RecipeLanguage getLang() {
		return lang;
	}

	public void setLang(RecipeLanguage lang) {
		this.lang = lang;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getExplanationForLlm() {
		return explanationForLlm;
	}

	public void setExplanationForLlm(String explanationForLlm) {
		this.explanationForLlm = explanationForLlm;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
