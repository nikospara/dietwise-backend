package eu.dietwise.tools.publish.model;

/**
 * One modification to a master table: an {@link InsertRow}, {@link UpdateRow} or {@link DeleteRow}. Each carries enough
 * information to render both the forward statement and its rollback.
 */
public sealed interface Change permits InsertRow, UpdateRow, DeleteRow {

	String table();
}
