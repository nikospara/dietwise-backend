package eu.dietwise.dao.jpa;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity that stores minimal user data but is capable of connecting the user to the IDM.
 * <p>
 * We duplicate minimal data from the IDM in our own database, so that user-related data can have a common reference.
 * </p>
 */
@Entity
@Table(name = "DW_USER")
public class UserEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "idm_id")
	private String idmId;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getIdmId() {
		return idmId;
	}

	public void setIdmId(String idmId) {
		this.idmId = idmId;
	}
}
