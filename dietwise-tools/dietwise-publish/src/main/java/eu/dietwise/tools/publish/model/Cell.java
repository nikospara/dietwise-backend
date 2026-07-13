package eu.dietwise.tools.publish.model;

import eu.dietwise.tools.publish.schema.ColumnType;

/**
 * A column name, its type and its value (or {@code null}) for one row. Self-describing so the renderer can emit it
 * without consulting the schema again.
 */
public record Cell(String column, ColumnType type, String value) {
}
