package eu.dietwise.dao.jpa.recommendations;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "DW_AGE_GROUP")
public class AgeGroupEntity {
	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "min")
	private int min;

	@Column(name = "max")
	private int max;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public int getMin() {
		return min;
	}

	public void setMin(int min) {
		this.min = min;
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
	}
}
