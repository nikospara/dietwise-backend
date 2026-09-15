package eu.dietwise.v1.types;

/**
 * Determines how the recipe score is affected by the existence of a component/ingredient that corresponds to a
 * GBD recommendation. {@link #ENCOURAGED} adds to the score, when the component/ingredient exists. {@link #LIMITED}
 * subtracts from the score, when the component/ingredient exists.
 */
public enum TypeOfRecommendation {
	LIMITED,
	ENCOURAGED
}
