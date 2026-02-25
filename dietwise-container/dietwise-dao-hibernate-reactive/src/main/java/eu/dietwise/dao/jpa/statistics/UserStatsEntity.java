package eu.dietwise.dao.jpa.statistics;

import static jakarta.persistence.FetchType.LAZY;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import eu.dietwise.dao.jpa.UserEntity;

@Entity
@Table(name = "DW_USER_STATS")
public class UserStatsEntity {
	@Id
	private UUID userId;

	@MapsId
	@OneToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private UserEntity user;

	@Column(name = "last_launched")
	private LocalDateTime lastLaunched;

	@Column(name = "last_seen")
	private LocalDateTime lastSeen;

	@Column(name = "days_launched")
	private Integer daysLaunched;

	@Column(name = "recipes_assessed")
	private Integer recipesAssessed;

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public UserEntity getUser() {
		return user;
	}

	public void setUser(UserEntity user) {
		this.user = user;
	}

	public LocalDateTime getLastLaunched() {
		return lastLaunched;
	}

	public void setLastLaunched(LocalDateTime lastLaunched) {
		this.lastLaunched = lastLaunched;
	}

	public LocalDateTime getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(LocalDateTime lastSeen) {
		this.lastSeen = lastSeen;
	}

	public Integer getDaysLaunched() {
		return daysLaunched;
	}

	public void setDaysLaunched(Integer daysLaunched) {
		this.daysLaunched = daysLaunched;
	}

	public Integer getRecipesAssessed() {
		return recipesAssessed;
	}

	public void setRecipesAssessed(Integer recipesAssessed) {
		this.recipesAssessed = recipesAssessed;
	}
}
