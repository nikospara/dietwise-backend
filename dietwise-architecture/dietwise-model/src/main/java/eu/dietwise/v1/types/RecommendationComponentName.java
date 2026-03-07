package eu.dietwise.v1.types;

import eu.dietwise.common.types.RepresentableAsString;

/**
 * The name of the component/ingredient that affects a recommendation. E.g., for the recommendation "Decrease red meat"
 * the name of the component that affects it is "red meat".
 */
public interface RecommendationComponentName extends RepresentableAsString {
}
