package eu.dietwise.common.dao.reactive.hibernate;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.NoResultException;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.reactive.ReactiveQuery;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny.SelectionQuery;

class ReactiveQueryImpl<R> implements ReactiveQuery<R> {
	private final SelectionQuery<R> query;
	private final Class<R> resultType;

	public ReactiveQueryImpl(SelectionQuery<R> query, Class<R> resultType) {
		this.query = query;
		this.resultType = resultType;
	}

	@Override
	public Uni<List<R>> getResultList() {
		return query.getResultList();
	}

	@Override
	public Uni<R> getSingleResult() {
		return query.getSingleResult()
				.onFailure(NoResultException.class)
				.transform(nre -> makeEntityNotFoundException(null, null, nre));
	}

	@Override
	public Uni<R> getSingleResult(Object id) {
		return query.getSingleResult()
				.onFailure(NoResultException.class)
				.transform(nre -> makeEntityNotFoundException(resultType, id, nre));
	}

	private EntityNotFoundException makeEntityNotFoundException(Class<?> entityClass, Object entityId, Throwable cause) {
		if (entityClass != null && entityId != null) {
			return new EntityNotFoundException(entityClass, entityId, cause);
		} else {
			return new EntityNotFoundException("No entity matches the criteria", cause);
		}
	}

	@Override
	public Uni<Optional<R>> getSingleOptionalResult() {
		return query.getSingleResultOrNull().map(Optional::ofNullable);
	}

	@Override
	public ReactiveQuery<R> setFirstResult(int firstResult) {
		query.setFirstResult(firstResult);
		return this;
	}

	@Override
	public ReactiveQuery<R> setMaxResults(int maxResults) {
		query.setMaxResults(maxResults);
		return this;
	}

	@Override
	public ReactiveQuery<R> setParameter(String parameter, Object argument) {
		query.setParameter(parameter, argument);
		return this;
	}

	@Override
	public Class<R> getResultType() {
		return resultType;
	}
}
