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
@IdClass(AlternativeIngredientTranslationEntityId.class)
@Table(name = "DW_ALTERNATIVE_INGREDIENT_TRANSLATION")
public class AlternativeIngredientTranslationEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "alternative_ingredient_id")
	private AlternativeIngredientEntity alternativeIngredient;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "name")
	private String name;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	public AlternativeIngredientEntity getAlternativeIngredient() {
		return alternativeIngredient;
	}

	public void setAlternativeIngredient(AlternativeIngredientEntity alternativeIngredient) {
		this.alternativeIngredient = alternativeIngredient;
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
