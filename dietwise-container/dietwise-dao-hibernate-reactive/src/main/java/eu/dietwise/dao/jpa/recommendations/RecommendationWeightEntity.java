package eu.dietwise.dao.jpa.recommendations;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.BiologicalGender;

@Entity
@Table(name = "DW_RECOMMENDATION_WEIGHT")
public class RecommendationWeightEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "recommendation_id")
	private RecommendationEntity recommendation;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "age_group_id")
	private AgeGroupEntity ageGroup;

	@Enumerated(STRING)
	@Column(name = "gender")
	private BiologicalGender gender;

	@Column(name = "weight")
	private BigDecimal weight;

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

	public AgeGroupEntity getAgeGroup() {
		return ageGroup;
	}

	public void setAgeGroup(AgeGroupEntity ageGroup) {
		this.ageGroup = ageGroup;
	}

	public BiologicalGender getGender() {
		return gender;
	}

	public void setGender(BiologicalGender gender) {
		this.gender = gender;
	}

	public BigDecimal getWeight() {
		return weight;
	}

	public void setWeight(BigDecimal weight) {
		this.weight = weight;
	}
}
