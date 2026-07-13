package eu.dietwise.tools.publish.schema;

/**
 * The SQL type of a column, as far as it matters for reading a value from the database and rendering it back into a
 * Liquibase changeset. Values flowing through the tool are always the canonical text form (or {@code null}); this type
 * only decides how the reader obtains that text and which Liquibase attribute the renderer uses to write it.
 */
public enum ColumnType {
	UUID,
	STRING,
	INT,
	BIGINT,
	BOOLEAN
}
