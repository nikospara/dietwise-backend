package eu.dietwise.tools.publish.schema;

/**
 * A foreign key from a mirrored child table to a mirrored parent table: the child {@code column} must match the primary
 * key of an existing row in {@code parentMaster}. The planner uses this to drop a Working Copy row whose parent will not
 * exist in master after the publish, so the generated changeset never violates the foreign key. The parent must have a
 * single-column primary key.
 */
public record ParentRef(String column, String parentMaster) {
}
