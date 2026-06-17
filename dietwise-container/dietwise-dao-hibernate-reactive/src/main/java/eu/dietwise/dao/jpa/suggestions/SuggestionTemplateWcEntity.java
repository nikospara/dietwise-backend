package eu.dietwise.dao.jpa.suggestions;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The Working Copy mirror of {@link SuggestionTemplateEntity}: a whole proposed Suggestion Template row staged for
 * publish. Sparse — a row exists only for a template that has a Staged Change. References are stored as raw ids and
 * resolved against published master union Working Copy at read time, so this table carries no foreign keys.
 */
@Entity
@Table(name = "DW_SUGGESTION_TEMPLATE_WC")
public class SuggestionTemplateWcEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "rule_id")
	private UUID ruleId;

	@Column(name = "alternative_ingredient_id")
	private UUID alternativeIngredientId;

	@Column(name = "alternative_order")
	private int alternativeOrder;

	@Column(name = "restriction")
	private String restriction;

	@Column(name = "equivalence")
	private String equivalence;

	@Column(name = "technique_notes")
	private String techniqueNotes;

	@Column(name = "version")
	private long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getRuleId() {
		return ruleId;
	}

	public void setRuleId(UUID ruleId) {
		this.ruleId = ruleId;
	}

	public UUID getAlternativeIngredientId() {
		return alternativeIngredientId;
	}

	public void setAlternativeIngredientId(UUID alternativeIngredientId) {
		this.alternativeIngredientId = alternativeIngredientId;
	}

	public int getAlternativeOrder() {
		return alternativeOrder;
	}

	public void setAlternativeOrder(int alternativeOrder) {
		this.alternativeOrder = alternativeOrder;
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

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
