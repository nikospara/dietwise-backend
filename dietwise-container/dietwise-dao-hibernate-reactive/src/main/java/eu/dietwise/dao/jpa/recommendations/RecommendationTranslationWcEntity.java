package eu.dietwise.dao.jpa.recommendations;

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
 * The Working Copy mirror of {@link RecommendationTranslationEntity}: a proposed per-language Recommendation name,
 * component for scoring and explanation translation staged for publish. Sparse — a row exists only for a translation
 * that differs from published master. The Recommendation is stored as a raw id and resolved against published master at
 * read time, so this table carries no foreign keys.
 */
@Entity
@IdClass(RecommendationTranslationWcEntityId.class)
@Table(name = "DW_RECOMMENDATION_TRANSLATION_WC")
public class RecommendationTranslationWcEntity {
	@Id
	@Column(name = "recommendation_id")
	private UUID recommendationId;

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

	@Column(name = "version")
	private long version;

	public UUID getRecommendationId() {
		return recommendationId;
	}

	public void setRecommendationId(UUID recommendationId) {
		this.recommendationId = recommendationId;
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

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
