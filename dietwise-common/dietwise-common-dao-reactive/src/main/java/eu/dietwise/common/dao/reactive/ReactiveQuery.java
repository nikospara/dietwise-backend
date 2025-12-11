package eu.dietwise.common.dao.reactive;

import java.util.List;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

/**
 * A wrapper around the selection query object of a reactive persistence implementation.
 * <p>
 * In the case of Hibernate Reactive, this wraps a {@code Mutiny.SelectionQuery}.
 *
 * @param <R> The result type
 */
public interface ReactiveQuery<R> {
	Uni<List<R>> getResultList();
	/**
	 * Return a single result matching the specified criteria, fail if there are no results or more than one.
	 */
	Uni<R> getSingleResult();
	/**
	 * Return a single result matching the specified criteria, fail if there are no results or more than one.
	 *
	 * @param id An optional id to be used only if a matching entity is not found for customizing the resulting exception
	 */
	Uni<R> getSingleResult(Object id);
	Uni<Optional<R>> getSingleOptionalResult();
	ReactiveQuery<R> setFirstResult(int firstResult);
	ReactiveQuery<R> setMaxResults(int maxResults);
	ReactiveQuery<R> setParameter(String parameter, Object argument);
	Class<R> getResultType();
}
