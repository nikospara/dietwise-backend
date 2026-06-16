package eu.dietwise.common.dao;

/**
 * A write was rejected because it would introduce a second entity with the same business key. The caller should
 * pick a different business key.
 */
public class DuplicateBusinessKeyException extends DaoException {
	public DuplicateBusinessKeyException(String message) {
		super(message);
	}
}
