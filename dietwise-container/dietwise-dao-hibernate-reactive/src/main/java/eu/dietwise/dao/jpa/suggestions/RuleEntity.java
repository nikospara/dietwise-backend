package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.FetchType.LAZY;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;

@Entity
@Table(name = "DW_RULE")
public class RuleEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "recommendation_id")
	private RecommendationEntity recommendation;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "trigger_ingredient_id")
	private TriggerIngredientEntity triggerIngredient;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "role_or_technique_id")
	private RoleOrTechniqueEntity roleOrTechnique;

	@Column(name = "cuisine")
	private String cuisine;

	@Column(name = "rationale")
	private String rationale;

	@OneToMany(mappedBy = "rule", fetch = LAZY)
	@OrderColumn(name = "alternative_order")
	private List<SuggestionTemplateEntity> alternatives;

	@OneToMany(mappedBy = "rule", fetch = LAZY)
	private Set<RuleTranslationEntity> translations;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public RecommendationEntity getRecommendation() {
		return recommendation;
	}

	public void setRecommendation(RecommendationEntity recommendation) {
		this.recommendation = recommendation;
	}

	public TriggerIngredientEntity getTriggerIngredient() {
		return triggerIngredient;
	}

	public void setTriggerIngredient(TriggerIngredientEntity triggerIngredient) {
		this.triggerIngredient = triggerIngredient;
	}

	public String getCuisine() {
		return cuisine;
	}

	public void setCuisine(String cuisine) {
		this.cuisine = cuisine;
	}

	public String getRationale() {
		return rationale;
	}

	public void setRationale(String rationale) {
		this.rationale = rationale;
	}

	public RoleOrTechniqueEntity getRoleOrTechnique() {
		return roleOrTechnique;
	}

	public void setRoleOrTechnique(RoleOrTechniqueEntity roleOrTechnique) {
		this.roleOrTechnique = roleOrTechnique;
	}

	public List<SuggestionTemplateEntity> getAlternatives() {
		return alternatives;
	}

	public void setAlternatives(List<SuggestionTemplateEntity> alternatives) {
		this.alternatives = alternatives;
	}

	public Set<RuleTranslationEntity> getTranslations() {
		return translations;
	}

	public void setTranslations(Set<RuleTranslationEntity> translations) {
		this.translations = translations;
	}
}
