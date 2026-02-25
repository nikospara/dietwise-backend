package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.FetchType.LAZY;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "DW_SUGGESTION_TEMPLATE")
public class SuggestionTemplateEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "rule_id")
	private RuleEntity rule;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "alternative_ingredient_id")
	private AlternativeIngredientEntity alternativeIngredient;

	@Column(name = "restriction")
	private String restriction;

	@Column(name = "equivalence")
	private String equivalence;

	@Column(name = "technique_notes")
	private String techniqueNotes;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public RuleEntity getRule() {
		return rule;
	}

	public void setRule(RuleEntity rule) {
		this.rule = rule;
	}

	public AlternativeIngredientEntity getAlternativeIngredient() {
		return alternativeIngredient;
	}

	public void setAlternativeIngredient(AlternativeIngredientEntity alternativeIngredient) {
		this.alternativeIngredient = alternativeIngredient;
	}

	public String getRestriction() {
		return restriction;
	}

	public void setRestriction(String restriction) {
		this.restriction = restriction;
	}

	public String getEquivalence() {
		return equivalence;
	}

	public void setEquivalence(String equivalence) {
		this.equivalence = equivalence;
	}

	public String getTechniqueNotes() {
		return techniqueNotes;
	}

	public void setTechniqueNotes(String techniqueNotes) {
		this.techniqueNotes = techniqueNotes;
	}
}
