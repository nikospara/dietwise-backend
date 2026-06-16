package eu.dietwise.common.dao;

/**
 * A write was rejected because the caller's base version no longer matches the entity's current version: someone
 * else changed it since the caller loaded it. The caller should reload the current state and redo the edit.
 */
public class StaleVersionException extends DaoException {
	private final Class<?> entityClass;
	private final Object entityId;

	public StaleVersionException(Class<?> entityClass, Object entityId) {
		super("Stale version" + (entityClass != null ? " " + entityClass.getName() : "") + (entityId != null ? " " + entityId : ""));
		this.entityClass = entityClass;
		this.entityId = entityId;
	}

	public StaleVersionException(Class<?> entityClass, Object entityId, String message) {
		super(message);
		this.entityClass = entityClass;
		this.entityId = entityId;
	}

	public Class<?> getEntityClass() {
		return entityClass;
	}

	public Object getEntityId() {
		return entityId;
	}
}
