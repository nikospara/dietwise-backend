package eu.dietwise.services.v1;

/**
 * How a Rule shown in the backoffice grid relates to published master.
 */
public enum RuleChangeState {
	/** No Staged Change, or the staged value equals published master. */
	UNCHANGED,
	/** An already-published Rule whose staged value differs from master. */
	CHANGED
}
