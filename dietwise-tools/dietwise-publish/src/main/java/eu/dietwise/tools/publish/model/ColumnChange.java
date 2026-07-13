package eu.dietwise.tools.publish.model;

import eu.dietwise.tools.publish.schema.ColumnType;

/**
 * A single column whose value is being changed by an update: its value before and after publish. {@code before} lets
 * the rollback restore the previous master value.
 */
public record ColumnChange(String column, ColumnType type, String before, String after) {
}
