package eu.dietwise.dao.jpa.suggestions;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The Working Copy mirror of {@link AlternativeIngredientEntity}: a whole proposed Alternative Ingredient row staged for
 * publish. Sparse — a row exists only for an Alternative Ingredient that has a Staged Change or was created in the
 * Working Copy. Shared master data: an edit here is seen by every Suggestion Template that references this entity.
 */
@Entity
@Table(name = "DW_ALTERNATIVE_INGREDIENT_WC")
public class AlternativeIngredientWcEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "name")
	private String name;

	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

	@Column(name = "version")
	private long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
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
