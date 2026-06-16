package eu.dietwise.dao.jpa.suggestions;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The Working Copy mirror of {@link RuleEntity}: a whole proposed Rule row staged for publish. Sparse — a row exists
 * only for a Rule that has a Staged Change or was created in the Working Copy. References are stored as raw ids and
 * resolved against published master union Working Copy at read time, so this table carries no foreign keys.
 */
@Entity
@Table(name = "DW_RULE_WC")
public class RuleWcEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "recommendation_id")
	private UUID recommendationId;

	@Column(name = "trigger_ingredient_id")
	private UUID triggerIngredientId;

	@Column(name = "role_or_technique_id")
	private UUID roleOrTechniqueId;

	@Column(name = "cuisine")
	private String cuisine;

	@Column(name = "rationale")
	private String rationale;

	@Column(name = "version")
	private long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getRecommendationId() {
		return recommendationId;
	}

	public void setRecommendationId(UUID recommendationId) {
		this.recommendationId = recommendationId;
	}

	public UUID getTriggerIngredientId() {
		return triggerIngredientId;
	}

	public void setTriggerIngredientId(UUID triggerIngredientId) {
		this.triggerIngredientId = triggerIngredientId;
	}

	public UUID getRoleOrTechniqueId() {
		return roleOrTechniqueId;
	}

	public void setRoleOrTechniqueId(UUID roleOrTechniqueId) {
		this.roleOrTechniqueId = roleOrTechniqueId;
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

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
