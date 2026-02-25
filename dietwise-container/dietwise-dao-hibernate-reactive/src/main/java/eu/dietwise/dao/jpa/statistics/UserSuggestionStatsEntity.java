package eu.dietwise.dao.jpa.statistics;

import static jakarta.persistence.FetchType.LAZY;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;

@Entity
@Table(name = "DW_USER_SUGGESTION_STATS")
@IdClass(UserSuggestionStatsId.class)
public class UserSuggestionStatsEntity {
	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Id
	@Column(name = "suggestion_template_id")
	private UUID suggestionTemplateId;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "user_id", insertable = false, updatable = false)
	private UserEntity user;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "suggestion_template_id", insertable = false, updatable = false)
	private SuggestionTemplateEntity suggestionTemplate;

	@Column(name = "times_suggested")
	private Integer timesSuggested;

	@Column(name = "times_accepted")
	private Integer timesAccepted;

	@Column(name = "times_rejected")
	private Integer timesRejected;

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public UUID getSuggestionTemplateId() {
		return suggestionTemplateId;
	}

	public void setSuggestionTemplateId(UUID suggestionTemplateId) {
		this.suggestionTemplateId = suggestionTemplateId;
	}

	public UserEntity getUser() {
		return user;
	}

	public void setUser(UserEntity user) {
		this.user = user;
	}

	public SuggestionTemplateEntity getSuggestionTemplate() {
		return suggestionTemplate;
	}

	public void setSuggestionTemplate(SuggestionTemplateEntity suggestionTemplate) {
		this.suggestionTemplate = suggestionTemplate;
	}

	public Integer getTimesSuggested() {
		return timesSuggested;
	}

	public void setTimesSuggested(Integer timesSuggested) {
		this.timesSuggested = timesSuggested;
	}

	public Integer getTimesAccepted() {
		return timesAccepted;
	}

	public void setTimesAccepted(Integer timesAccepted) {
		this.timesAccepted = timesAccepted;
	}

	public Integer getTimesRejected() {
		return timesRejected;
	}

	public void setTimesRejected(Integer timesRejected) {
		this.timesRejected = timesRejected;
	}
}
