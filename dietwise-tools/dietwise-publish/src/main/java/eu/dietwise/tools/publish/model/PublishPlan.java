package eu.dietwise.tools.publish.model;

import java.util.List;

/**
 * A structural description of everything a publish does, independent of Liquibase: the master rows to insert, update,
 * and delete, the Working Copy tables to clear (with their rows kept for the rollback), and the fingerprint of the
 * Working Copy at generation time used to guard the changeset against a Working Copy that changed since.
 *
 * <p>{@link #inserts} are ordered ascending by foreign-key rank (parents first); {@link #deletes} descending (children
 * first); {@link #workingCopy} ascending. The renderer reverses these as needed for the rollback.
 */
public record PublishPlan(
		List<InsertRow> inserts,
		List<UpdateRow> updates,
		List<DeleteRow> deletes,
		List<TableSnapshot> workingCopy,
		long workingCopyFingerprint) {

	public boolean isEmpty() {
		return inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty() && workingCopy.isEmpty();
	}
}
