package eu.dietwise.dao.jpa.recommendations;

import static jakarta.persistence.EnumType.STRING;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.RecommendationWeight;

@Entity
@Table(name = "DW_RECOMMENDATION")
public class RecommendationEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "name")
	private String name;

	@Column(name = "component_for_scoring")
	private String componentForScoring;

	@Enumerated(STRING)
	@Column(name = "weight")
	private RecommendationWeight weight;

	/**
	 * The optional explanation that applies to the {@link #componentForScoring} to make the LLM understand.
	 */
	@Column(name = "explanation_for_llm")
	private String explanationForLlm;

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

	public String getComponentForScoring() {
		return componentForScoring;
	}

	public void setComponentForScoring(String componentForScoring) {
		this.componentForScoring = componentForScoring;
	}

	public RecommendationWeight getWeight() {
		return weight;
	}

	public void setWeight(RecommendationWeight weight) {
		this.weight = weight;
	}

	public String getExplanationForLlm() {
		return explanationForLlm;
	}

	public void setExplanationForLlm(String explanationForLlm) {
		this.explanationForLlm = explanationForLlm;
	}
}
