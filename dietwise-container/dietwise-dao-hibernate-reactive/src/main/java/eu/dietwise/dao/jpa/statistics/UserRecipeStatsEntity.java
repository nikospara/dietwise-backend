package eu.dietwise.dao.jpa.statistics;

import static jakarta.persistence.FetchType.LAZY;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.dao.jpa.UserEntity;

@Entity
@Table(name = "DW_USER_RECIPE_STATS")
@IdClass(UserRecipeStatsId.class)
public class UserRecipeStatsEntity {
	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Id
	@Column(name = "application_id")
	private String applicationId;

	@Id
	@Column(name = "recipe_url")
	private String recipeUrl;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "user_id", insertable = false, updatable = false)
	private UserEntity user;

	@Column(name = "recipe_name")
	private String recipeName;

	@Column(name = "times_assessed")
	private Integer timesAssessed;

	@Column(name = "last_assessed")
	private LocalDateTime lastAssessed;

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public String getRecipeUrl() {
		return recipeUrl;
	}

	public void setRecipeUrl(String recipeUrl) {
		this.recipeUrl = recipeUrl;
	}

	public UserEntity getUser() {
		return user;
	}

	public void setUser(UserEntity user) {
		this.user = user;
	}

	public String getRecipeName() {
		return recipeName;
	}

	public void setRecipeName(String recipeName) {
		this.recipeName = recipeName;
	}

	public Integer getTimesAssessed() {
		return timesAssessed;
	}

	public void setTimesAssessed(Integer timesAssessed) {
		this.timesAssessed = timesAssessed;
	}

	public LocalDateTime getLastAssessed() {
		return lastAssessed;
	}

	public void setLastAssessed(LocalDateTime lastAssessed) {
		this.lastAssessed = lastAssessed;
	}
}
