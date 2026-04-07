package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.Country;

@Entity
@IdClass(AlternativeIngredientSeasonalityEntityId.class)
@Table(name = "DW_ALTERNATIVE_INGREDIENT_SEASONALITY")
public class AlternativeIngredientSeasonalityEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "alternative_ingredient_id")
	private AlternativeIngredientEntity alternativeIngredient;

	@Id
	@Column(name = "country")
	private String countryCode2;

	@Column(name = "month_from")
	private Integer monthFrom;

	@Column(name = "month_to")
	private Integer monthTo;

	public AlternativeIngredientEntity getAlternativeIngredient() {
		return alternativeIngredient;
	}

	public void setAlternativeIngredient(AlternativeIngredientEntity alternativeIngredient) {
		this.alternativeIngredient = alternativeIngredient;
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
}
