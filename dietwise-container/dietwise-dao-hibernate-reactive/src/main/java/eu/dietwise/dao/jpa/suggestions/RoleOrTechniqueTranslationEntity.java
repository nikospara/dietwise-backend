package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.RecipeLanguage;

@Entity
@IdClass(RoleOrTechniqueTranslationEntityId.class)
@Table(name = "DW_ROLE_OR_TECHNIQUE_TRANSLATION")
public class RoleOrTechniqueTranslationEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "role_or_technique_id")
	private RoleOrTechniqueEntity roleOrTechnique;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "name")
	private String name;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	public RoleOrTechniqueEntity getRoleOrTechnique() {
		return roleOrTechnique;
	}

	public void setRoleOrTechnique(RoleOrTechniqueEntity roleOrTechnique) {
		this.roleOrTechnique = roleOrTechnique;
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
}
