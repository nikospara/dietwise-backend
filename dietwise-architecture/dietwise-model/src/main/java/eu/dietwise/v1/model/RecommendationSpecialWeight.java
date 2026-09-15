package eu.dietwise.v1.model;

import java.math.BigDecimal;

import eu.dietwise.v1.types.TypeOfRecommendation;
import org.immutables.value.Value;

/**
 * How one recommendation component takes part in the recipe score: whether its presence raises or lowers the score,
 * and how much it counts against the other components of the same type. The weight is the recommendation value that
 * applies to the age group and biological gender of the user; a component with no applicable value weighs nothing.
 */
@Value.Immutable
public interface RecommendationSpecialWeight {
	TypeOfRecommendation getTypeOfRecommendation();

	BigDecimal getWeight();
}
