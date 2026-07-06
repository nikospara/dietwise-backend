package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.EnumType.STRING;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;

/**
 * The Working Copy mirror of {@link AlternativeIngredientCostEntity}: a proposed per-country indicative cost staged for
 * publish. Sparse — a row exists only for an Alternative Ingredient and country whose cost differs from published
 * master. A {@code null} {@code cost} means a staged empty (the cost is proposed to be removed).
 */
@Entity
@IdClass(AlternativeIngredientCostWcEntityId.class)
@Table(name = "DW_ALTERNATIVE_INGREDIENT_COST_WC")
public class AlternativeIngredientCostWcEntity {
	@Id
	@Column(name = "alternative_ingredient_id")
	private UUID alternativeIngredientId;

	@Id
	@Column(name = "country")
	private String countryCode2;

	@Enumerated(STRING)
	@Column(name = "cost")
	private Cost cost;

	@Column(name = "version")
	private long version;

	public UUID getAlternativeIngredientId() {
		return alternativeIngredientId;
	}

	public void setAlternativeIngredientId(UUID alternativeIngredientId) {
		this.alternativeIngredientId = alternativeIngredientId;
	}

	public Country getCountry() {
		return Country.fromCode2(countryCode2);
	}

	public void setCountry(Country country) {
		this.countryCode2 = country == null ? null : country.getCode2();
	}

	public Cost getCost() {
		return cost;
	}

	public void setCost(Cost cost) {
		this.cost = cost;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
