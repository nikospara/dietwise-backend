package eu.dietwise.tools.publish.schema;

import java.util.List;

/**
 * Pairs a master table with its Working Copy mirror and describes how the mirror is published. {@link #order} is the
 * foreign-key rank: rows of a lower order are inserted before rows of a higher order (and deleted in the reverse
 * order), so that a parent always exists before its children.
 */
public record MirrorSpec(TableSpec master, TableSpec wc, PublishKind kind, int order) {

	private static final String VERSION = "version";

	/**
	 * The Working Copy columns carrying the proposed values: every mirror column that is neither part of the primary
	 * key nor the optimistic-concurrency {@code version}. These are the columns compared against master and written on
	 * an insert or update.
	 */
	public List<String> payloadColumns() {
		return wc.columns().stream()
				.map(ColumnSpec::name)
				.filter(name -> !wc.isPrimaryKey(name) && !name.equals(VERSION))
				.toList();
	}

	/** Whether the Working Copy mirror carries a {@code version} column (all mirrors do except the link-delta table). */
	public boolean isVersioned() {
		return wc.hasColumn(VERSION);
	}
}
