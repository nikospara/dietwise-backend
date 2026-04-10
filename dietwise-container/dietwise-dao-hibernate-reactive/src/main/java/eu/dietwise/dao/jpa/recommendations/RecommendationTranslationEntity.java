package eu.dietwise.dao.jpa.recommendations;

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
@IdClass(RecommendationTranslationEntityId.class)
@Table(name = "DW_RECOMMENDATION_TRANSLATION")
public class RecommendationTranslationEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "recommendation_id")
	private RecommendationEntity recommendation;

	@Id
	@Enumerated(STRING)
	@Column(name = "lang")
	private RecipeLanguage lang;

	@Column(name = "name")
	private String name;

	@Column(name = "component_for_scoring")
	private String componentForScoring;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	public RecommendationEntity getRecommendation() {
		return recommendation;
	}

	public void setRecommendation(RecommendationEntity recommendation) {
		this.recommendation = recommendation;
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

	public String getComponentForScoring() {
		return componentForScoring;
	}

	public void setComponentForScoring(String componentForScoring) {
		this.componentForScoring = componentForScoring;
	}

	public String getExplanationForLlm() {
		return explanationForLlm;
	}

	public void setExplanationForLlm(String explanationForLlm) {
		this.explanationForLlm = explanationForLlm;
	}
}
