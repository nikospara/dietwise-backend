package eu.dietwise.dao.jpa.suggestions;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.Country;

/**
 * The Working Copy mirror of {@link AlternativeIngredientSeasonalityEntity}: a proposed per-country in-season month
 * range staged for publish. Sparse — a row exists only for an Alternative Ingredient and country whose seasonality
 * differs from published master. Both {@code monthFrom} and {@code monthTo} null means a staged empty (the seasonality
 * is proposed to be removed).
 */
@Entity
@IdClass(AlternativeIngredientSeasonalityWcEntityId.class)
@Table(name = "DW_ALTERNATIVE_INGREDIENT_SEASONALITY_WC")
public class AlternativeIngredientSeasonalityWcEntity {
	@Id
	@Column(name = "alternative_ingredient_id")
	private UUID alternativeIngredientId;

	@Id
	@Column(name = "country")
	private String countryCode2;

	@Column(name = "month_from")
	private Integer monthFrom;

	@Column(name = "month_to")
	private Integer monthTo;

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

	public Integer getMonthFrom() {
		return monthFrom;
	}

	public void setMonthFrom(Integer monthFrom) {
		this.monthFrom = monthFrom;
	}

	public Integer getMonthTo() {
		return monthTo;
	}

	public void setMonthTo(Integer monthTo) {
		this.monthTo = monthTo;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
