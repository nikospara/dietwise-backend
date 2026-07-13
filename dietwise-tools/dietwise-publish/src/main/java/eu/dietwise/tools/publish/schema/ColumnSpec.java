package eu.dietwise.tools.publish.schema;

/**
 * A single column of a table: its name and the type that governs how its value is read and rendered.
 */
public record ColumnSpec(String name, ColumnType type) {

	public static ColumnSpec uuid(String name) {
		return new ColumnSpec(name, ColumnType.UUID);
	}

	public static ColumnSpec text(String name) {
		return new ColumnSpec(name, ColumnType.STRING);
	}

	public static ColumnSpec integer(String name) {
		return new ColumnSpec(name, ColumnType.INT);
	}

	public static ColumnSpec bigint(String name) {
		return new ColumnSpec(name, ColumnType.BIGINT);
	}

	public static ColumnSpec bool(String name) {
		return new ColumnSpec(name, ColumnType.BOOLEAN);
	}
}
