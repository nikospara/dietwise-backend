package eu.dietwise.dao.jpa.statistics;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserRecipeStatsId implements Serializable {
	private UUID userId;
	private String applicationId;
	private String recipeUrl;

	public UserRecipeStatsId() {
	}

	public UserRecipeStatsId(UUID userId, String applicationId, String recipeUrl) {
		this.userId = userId;
		this.applicationId = applicationId;
		this.recipeUrl = recipeUrl;
	}

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

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UserRecipeStatsId that)) return false;
		return Objects.equals(getUserId(), that.getUserId())
				&& Objects.equals(getApplicationId(), that.getApplicationId())
				&& Objects.equals(getRecipeUrl(), that.getRecipeUrl());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUserId(), getApplicationId(), getRecipeUrl());
	}
}
