package eu.dietwise.tools.publish.schema;

/**
 * A single column of a table: its name, the type that governs how its value is read and rendered, and whether the
 * column accepts null. Nullability is meaningful for master columns: the publish planner drops a Working Copy row whose
 * published value would be null in a non-nullable master column, so the generated changeset never violates a NOT NULL
 * constraint. Working Copy columns are staging columns and are left nullable.
 */
public record ColumnSpec(String name, ColumnType type, boolean nullable) {

	public static ColumnSpec uuid(String name) {
		return new ColumnSpec(name, ColumnType.UUID, true);
	}

	public static ColumnSpec text(String name) {
		return new ColumnSpec(name, ColumnType.STRING, true);
	}

	public static ColumnSpec integer(String name) {
		return new ColumnSpec(name, ColumnType.INT, true);
	}

	public static ColumnSpec bigint(String name) {
		return new ColumnSpec(name, ColumnType.BIGINT, true);
	}

	public static ColumnSpec bool(String name) {
		return new ColumnSpec(name, ColumnType.BOOLEAN, true);
	}

	/** The same column constrained NOT NULL. Marks a non-nullable master column in the registry. */
	public ColumnSpec notNull() {
		return new ColumnSpec(name, type, false);
	}
}
