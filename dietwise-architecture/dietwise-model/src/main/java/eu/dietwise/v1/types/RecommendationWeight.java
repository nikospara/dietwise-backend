package eu.dietwise.v1.types;

/**
 * Determines how the recipe score is affected by the existence of a component/ingredient that corresponds to a
 * GBD recommendation. {@link #ENCOURAGED} adds to the score, when the component/ingredient exists. {@link #LIMITED}
 * adds to the score if the component/ingredient does not exist.
 */
public enum RecommendationWeight {
	LIMITED,
	ENCOURAGED
}
