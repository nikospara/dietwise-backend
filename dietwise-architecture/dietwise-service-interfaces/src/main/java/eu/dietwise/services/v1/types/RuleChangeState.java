package eu.dietwise.services.v1.types;

/**
 * How a Rule shown in the backoffice grid relates to published master.
 */
public enum RuleChangeState {
	/** No Staged Change, or the staged value equals published master. */
	UNCHANGED,
	/** An already-published Rule whose staged value differs from master. */
	CHANGED,
	/** A Rule that exists only in the Working Copy and has not been published yet. */
	NEW
}
