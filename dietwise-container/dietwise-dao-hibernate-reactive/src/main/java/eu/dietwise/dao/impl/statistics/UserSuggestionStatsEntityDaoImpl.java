package eu.dietwise.dao.impl.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder.In;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.statistics.UserSuggestionStatsEntity;
import eu.dietwise.dao.jpa.statistics.UserSuggestionStatsEntity_;
import eu.dietwise.dao.jpa.statistics.UserSuggestionStatsId;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserSuggestionStatsEntityDaoImpl implements UserSuggestionStatsEntityDao {
	private static final int MAX_SUGGESTION_STATS_IDS = 20;

	@Override
	public Uni<Map<SuggestionTemplateId, SuggestionStats>> retrieveTotalSuggestionStats(ReactivePersistenceContext em, Set<SuggestionTemplateId> ids) {
		if (ids.isEmpty()) {
			return Uni.createFrom().item(Map.of());
		}
		if (ids.size() > MAX_SUGGESTION_STATS_IDS) {
			throw new IllegalArgumentException("Too many suggestion ids: " + ids.size());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(UserSuggestionStatsEntity.class);
		Path<UUID> suggestionTemplateId = root.get(UserSuggestionStatsEntity_.suggestionTemplateId);
		Path<Integer> timesSuggested = root.get(UserSuggestionStatsEntity_.timesSuggested);
		Path<Integer> timesAccepted = root.get(UserSuggestionStatsEntity_.timesAccepted);
		Path<Integer> timesRejected = root.get(UserSuggestionStatsEntity_.timesRejected);
		Expression<Integer> sumTimesSuggested = cb.sum(timesSuggested);
		Expression<Integer> sumTimesAccepted = cb.sum(timesAccepted);
		Expression<Integer> sumTimesRejected = cb.sum(timesRejected);

		q.select(cb.tuple(suggestionTemplateId, sumTimesSuggested, sumTimesAccepted, sumTimesRejected));
		q.where(in(cb, root.get(UserSuggestionStatsEntity_.suggestionTemplateId), ids));
		q.groupBy(suggestionTemplateId);

		Map<SuggestionTemplateId, SuggestionStats> result = makeEmptyStatsMap(ids);
		return em.createQuery(q).getResultList().map(rows -> {
			rows.forEach(row -> mergeIntoMap(result, row, sumTimesSuggested, sumTimesAccepted, sumTimesRejected));
			return result;
		});
	}

	@Override
	public Uni<Map<SuggestionTemplateId, SuggestionStats>> retrieveUserSuggestionStats(ReactivePersistenceContext em, HasUserId userId, Set<SuggestionTemplateId> ids) {
		if (ids.isEmpty()) {
			return Uni.createFrom().item(Map.of());
		}
		if (ids.size() > MAX_SUGGESTION_STATS_IDS) {
			throw new IllegalArgumentException("Too many suggestion ids: " + ids.size());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var root = q.from(UserSuggestionStatsEntity.class);
		Path<UUID> suggestionTemplateId = root.get(UserSuggestionStatsEntity_.suggestionTemplateId);
		Path<Integer> timesSuggested = root.get(UserSuggestionStatsEntity_.timesSuggested);
		Path<Integer> timesAccepted = root.get(UserSuggestionStatsEntity_.timesAccepted);
		Path<Integer> timesRejected = root.get(UserSuggestionStatsEntity_.timesRejected);

		q.select(cb.tuple(suggestionTemplateId, timesSuggested, timesAccepted, timesRejected));
		UUID userIdUuid = UUID.fromString(userId.getId().asString());
		q.where(cb.and(cb.equal(root.get(UserSuggestionStatsEntity_.userId), userIdUuid), in(cb, root.get(UserSuggestionStatsEntity_.suggestionTemplateId), ids)));

		Map<SuggestionTemplateId, SuggestionStats> result = makeEmptyStatsMap(ids);
		return em.createQuery(q).getResultList().map(rows -> {
			rows.forEach(row -> mergeIntoMap(result, row, timesSuggested, timesAccepted, timesRejected));
			return result;
		});
	}

	@Override
	public Uni<Integer> increaseTimesSuggested(ReactivePersistenceTxContext tx, HasUserId userId, SuggestionTemplateId suggestionId) {
		return getOrCreate(tx, UUID.fromString(userId.getId().asString()), suggestionId.asUuid())
				.invoke(entity -> entity.setTimesSuggested(toIntOr0(entity.getTimesSuggested()) + 1))
				.flatMap(tx::merge)
				.map(UserSuggestionStatsEntity::getTimesSuggested);
	}

	@Override
	public Uni<Integer> increaseTimesAccepted(ReactivePersistenceTxContext tx, HasUserId userId, SuggestionTemplateId suggestionId) {
		return getOrCreate(tx, UUID.fromString(userId.getId().asString()), suggestionId.asUuid())
				.invoke(entity -> entity.setTimesAccepted(toIntOr0(entity.getTimesAccepted()) + 1))
				.flatMap(tx::merge)
				.map(UserSuggestionStatsEntity::getTimesAccepted);
	}

	@Override
	public Uni<Integer> decreaseTimesAccepted(ReactivePersistenceTxContext tx, HasUserId userId, SuggestionTemplateId suggestionId) {
		return getOrCreate(tx, UUID.fromString(userId.getId().asString()), suggestionId.asUuid())
				.invoke(entity -> entity.setTimesAccepted(Math.max(0, toIntOr0(entity.getTimesAccepted()) - 1)))
				.flatMap(tx::merge)
				.map(UserSuggestionStatsEntity::getTimesAccepted);
	}

	@Override
	public Uni<Integer> increaseTimesRejected(ReactivePersistenceTxContext tx, HasUserId userId, SuggestionTemplateId suggestionId) {
		return getOrCreate(tx, UUID.fromString(userId.getId().asString()), suggestionId.asUuid())
				.invoke(entity -> entity.setTimesRejected(toIntOr0(entity.getTimesRejected()) + 1))
				.flatMap(tx::merge)
				.map(UserSuggestionStatsEntity::getTimesRejected);
	}

	@Override
	public Uni<Integer> decreaseTimesRejected(ReactivePersistenceTxContext tx, HasUserId userId, SuggestionTemplateId suggestionId) {
		return getOrCreate(tx, UUID.fromString(userId.getId().asString()), suggestionId.asUuid())
				.invoke(entity -> entity.setTimesRejected(Math.max(0, toIntOr0(entity.getTimesRejected()) - 1)))
				.flatMap(tx::merge)
				.map(UserSuggestionStatsEntity::getTimesRejected);
	}

	private Predicate in(jakarta.persistence.criteria.CriteriaBuilder cb, Path<UUID> path, Set<SuggestionTemplateId> ids) {
		In<UUID> in = cb.in(path);
		ids.forEach(id -> in.value(id.asUuid()));
		return in;
	}

	private Map<SuggestionTemplateId, SuggestionStats> makeEmptyStatsMap(Set<SuggestionTemplateId> ids) {
		Map<SuggestionTemplateId, SuggestionStats> result = new LinkedHashMap<>();
		ids.forEach(id -> result.put(id, new SuggestionStats(0, 0, 0)));
		return result;
	}

	private void mergeIntoMap(
			Map<SuggestionTemplateId, SuggestionStats> map,
			Tuple tuple,
			Expression<? extends Number> timesSuggestedExpr,
			Expression<? extends Number> timesAcceptedExpr,
			Expression<? extends Number> timesRejectedExpr
	) {
		UUID suggestionTemplateUuid = tuple.get(0, UUID.class);
		SuggestionTemplateId suggestionTemplateId = new GenericSuggestionTemplateId(suggestionTemplateUuid.toString());
		int timesSuggested = toIntOr0(tuple.get(timesSuggestedExpr));
		int timesAccepted = toIntOr0(tuple.get(timesAcceptedExpr));
		int timesRejected = toIntOr0(tuple.get(timesRejectedExpr));
		map.put(suggestionTemplateId, new SuggestionStats(timesSuggested, timesAccepted, timesRejected));
	}

	private Uni<UserSuggestionStatsEntity> getOrCreate(ReactivePersistenceTxContext tx, UUID userId, UUID suggestionTemplateId) {
		var id = new UserSuggestionStatsId();
		id.setUserId(userId);
		id.setSuggestionTemplateId(suggestionTemplateId);
		return tx.find(UserSuggestionStatsEntity.class, id)
				.flatMap(entity -> entity != null
						? Uni.createFrom().item(entity)
						: create(tx, userId, suggestionTemplateId));
	}

	private Uni<UserSuggestionStatsEntity> create(ReactivePersistenceTxContext tx, UUID userId, UUID suggestionTemplateId) {
		return tx.find(UserEntity.class, userId)
				.flatMap(user -> tx.find(SuggestionTemplateEntity.class, suggestionTemplateId)
						.flatMap(suggestionTemplate -> {
							UserSuggestionStatsEntity entity = new UserSuggestionStatsEntity();
							entity.setUserId(userId);
							entity.setSuggestionTemplateId(suggestionTemplateId);
							entity.setUser(user);
							entity.setSuggestionTemplate(suggestionTemplate);
							entity.setTimesSuggested(0);
							entity.setTimesAccepted(0);
							entity.setTimesRejected(0);
							return tx.persist(entity);
						}));
	}

	private int toIntOr0(Number value) {
		return value == null ? 0 : value.intValue();
	}
}
