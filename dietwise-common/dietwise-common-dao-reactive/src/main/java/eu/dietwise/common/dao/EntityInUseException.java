package eu.dietwise.common.dao;

/**
 * A delete was rejected because the entity is still in use: it is referenced by other entities, or it is published
 * master data that the live system depends on. The caller should remove the references, or accept that published data
 * cannot be discarded, rather than retry.
 */
public class EntityInUseException extends DaoException {
	public EntityInUseException(String message) {
		super(message);
	}
}
