package eu.dietwise.dao.jpa.recommendations;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The Working Copy mirror of {@link RecommendationEntity}: a staged edit to a Recommendation's explanation for the LLM.
 * Sparse — a row exists only while the explanation differs from published master. A Recommendation is never created or
 * renamed in the Working Copy, so only the editable explanation and the optimistic-concurrency version are mirrored; the
 * id always equals the master Recommendation id.
 */
@Entity
@Table(name = "DW_RECOMMENDATION_WC")
public class RecommendationWcEntity {
	@Id
	@Column(name = "id")
	private UUID id;

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
