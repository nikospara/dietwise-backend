package eu.dietwise.dao.jpa.suggestions;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;

@Entity
@IdClass(AlternativeIngredientCostEntityId.class)
@Table(name = "DW_ALTERNATIVE_INGREDIENT_COST")
public class AlternativeIngredientCostEntity {
	@Id
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "alternative_ingredient_id")
	private AlternativeIngredientEntity alternativeIngredient;

	@Id
	@Column(name = "country")
	private String countryCode2;

	@Enumerated(STRING)
	@Column(name = "cost")
	private Cost cost;

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

	public Cost getCost() {
		return cost;
	}

	public void setCost(Cost cost) {
		this.cost = cost;
	}
}
