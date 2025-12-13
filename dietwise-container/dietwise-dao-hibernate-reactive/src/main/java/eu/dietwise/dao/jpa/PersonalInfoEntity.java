package eu.dietwise.dao.jpa;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.BiologicalGender;

@Entity
@Table(name = "DW_PERSONAL_INFO")
public class PersonalInfoEntity {
	@Id
	private UUID userId;

	@MapsId
	@OneToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private UserEntity user;

	/** The biological gender. */
	@Enumerated(STRING)
	@Column(name = "gender")
	private BiologicalGender gender;

	/** The year of birth. */
	@Column(name = "year_of_birth")
	private Integer yearOfBirth;

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

	public BiologicalGender getGender() {
		return gender;
	}

	public void setGender(BiologicalGender gender) {
		this.gender = gender;
	}

	public Integer getYearOfBirth() {
		return yearOfBirth;
	}

	public void setYearOfBirth(Integer yearOfBirth) {
		this.yearOfBirth = yearOfBirth;
	}
}
