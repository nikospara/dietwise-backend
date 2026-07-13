package eu.dietwise.tools.publish.schema;

/**
 * How a Working Copy table's rows are published into its master table.
 */
public enum PublishKind {

	/**
	 * The Working Copy row is a full snapshot of the proposed entity. A row whose primary key is absent from master is
	 * inserted; a row whose primary key is present is updated for the columns that differ. Master rows are never removed.
	 */
	SNAPSHOT,

	/**
	 * The Working Copy row is a presence delta on a link (join) table: {@code present = true} adds the link if missing,
	 * {@code present = false} removes it if present.
	 */
	LINK_DELTA,

	/**
	 * Like {@link #SNAPSHOT}, but the master payload columns are NOT NULL while the Working Copy allows them to be null.
	 * A Working Copy row whose payload is entirely null is a staged empty and removes the master row.
	 */
	NULLABLE_PAYLOAD
}
