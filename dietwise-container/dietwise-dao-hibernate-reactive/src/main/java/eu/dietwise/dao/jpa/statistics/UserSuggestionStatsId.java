package eu.dietwise.dao.jpa.statistics;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserSuggestionStatsId implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private UUID userId;

	private String applicationId;

	private UUID suggestionTemplateId;

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

	public UUID getSuggestionTemplateId() {
		return suggestionTemplateId;
	}

	public void setSuggestionTemplateId(UUID suggestionTemplateId) {
		this.suggestionTemplateId = suggestionTemplateId;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof UserSuggestionStatsId other)) {
			return false;
		}
		return Objects.equals(userId, other.userId) && Objects.equals(applicationId, other.applicationId) && Objects.equals(suggestionTemplateId, other.suggestionTemplateId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userId, applicationId, suggestionTemplateId);
	}
}
