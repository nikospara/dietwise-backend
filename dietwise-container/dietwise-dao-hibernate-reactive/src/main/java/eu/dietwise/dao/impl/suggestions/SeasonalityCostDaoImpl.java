package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntityId;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostWcEntityId;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostWcEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntityId;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityWcEntityId;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityWcEntity_;
import eu.dietwise.dao.suggestions.SeasonalityCostDao;
import eu.dietwise.services.model.suggestions.AlternativeIngredientCountryKey;
import eu.dietwise.services.model.suggestions.StagedCost;
import eu.dietwise.services.model.suggestions.StagedSeasonality;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.Seasonality;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SeasonalityCostDaoImpl implements SeasonalityCostDao {
	@Override
	public Uni<Map<AlternativeIngredientCountryKey, Seasonality>> findMasterSeasonality(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientSeasonalityEntity> s = q.from(AlternativeIngredientSeasonalityEntity.class);
		q.select(cb.tuple(
				s.get(AlternativeIngredientSeasonalityEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id),
				s.get(AlternativeIngredientSeasonalityEntity_.countryCode2),
				s.get(AlternativeIngredientSeasonalityEntity_.monthFrom),
				s.get(AlternativeIngredientSeasonalityEntity_.monthTo)));
		return em.createQuery(q).getResultList().map(rows -> {
			Map<AlternativeIngredientCountryKey, Seasonality> byKey = new HashMap<>();
			for (Tuple row : rows) {
				byKey.put(
						keyOf(row.get(0, UUID.class), row.get(1, String.class)),
						ImmutableSeasonality.builder().monthFrom(row.get(2, Integer.class)).monthTo(row.get(3, Integer.class)).build());
			}
			return byKey;
		});
	}

	@Override
	public Uni<Map<AlternativeIngredientCountryKey, StagedSeasonality>> findStagedSeasonality(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientSeasonalityWcEntity.class);
		q.from(AlternativeIngredientSeasonalityWcEntity.class);
		return em.createQuery(q).getResultList().map(rows -> {
			Map<AlternativeIngredientCountryKey, StagedSeasonality> byKey = new HashMap<>();
			for (AlternativeIngredientSeasonalityWcEntity row : rows) {
				byKey.put(
						new AlternativeIngredientCountryKey(row.getAlternativeIngredientId(), row.getCountry()),
						new StagedSeasonality(row.getMonthFrom(), row.getMonthTo(), row.getVersion()));
			}
			return byKey;
		});
	}

	@Override
	public Uni<Map<AlternativeIngredientCountryKey, Cost>> findMasterCost(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientCostEntity> c = q.from(AlternativeIngredientCostEntity.class);
		q.select(cb.tuple(
				c.get(AlternativeIngredientCostEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id),
				c.get(AlternativeIngredientCostEntity_.countryCode2),
				c.get(AlternativeIngredientCostEntity_.cost)));
		return em.createQuery(q).getResultList().map(rows -> {
			Map<AlternativeIngredientCountryKey, Cost> byKey = new HashMap<>();
			for (Tuple row : rows) {
				byKey.put(keyOf(row.get(0, UUID.class), row.get(1, String.class)), row.get(2, Cost.class));
			}
			return byKey;
		});
	}

	@Override
	public Uni<Map<AlternativeIngredientCountryKey, StagedCost>> findStagedCost(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientCostWcEntity.class);
		q.from(AlternativeIngredientCostWcEntity.class);
		return em.createQuery(q).getResultList().map(rows -> {
			Map<AlternativeIngredientCountryKey, StagedCost> byKey = new HashMap<>();
			for (AlternativeIngredientCostWcEntity row : rows) {
				byKey.put(
						new AlternativeIngredientCountryKey(row.getAlternativeIngredientId(), row.getCountry()),
						new StagedCost(row.getCost(), row.getVersion()));
			}
			return byKey;
		});
	}

	@Override
	public Uni<Void> stageSeasonality(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo, long baseVersion) {
		return forc(
				tx.find(AlternativeIngredientSeasonalityWcEntity.class, new AlternativeIngredientSeasonalityWcEntityId(alternativeIngredientId, country)),
				_ -> tx.find(AlternativeIngredientSeasonalityEntity.class, new AlternativeIngredientSeasonalityEntityId(alternativeIngredientId, country)),
				(existing, master) -> applySeasonalityEdit(tx, alternativeIngredientId, country, monthFrom, monthTo, baseVersion, existing, master)
		);
	}

	private Uni<Void> applySeasonalityEdit(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo, long baseVersion, AlternativeIngredientSeasonalityWcEntity existing, AlternativeIngredientSeasonalityEntity master) {
		Integer masterFrom = master == null ? null : master.getMonthFrom();
		Integer masterTo = master == null ? null : master.getMonthTo();
		boolean matchesMaster = Objects.equals(monthFrom, masterFrom) && Objects.equals(monthTo, masterTo);
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientSeasonalityEntity.class, alternativeIngredientId));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStagedSeasonality(tx, alternativeIngredientId, country, monthFrom, monthTo);
		}
		return matchesMaster
				? deleteStagedSeasonality(tx, alternativeIngredientId, country, baseVersion)
				: bumpStagedSeasonality(tx, alternativeIngredientId, country, monthFrom, monthTo, baseVersion);
	}

	private Uni<Void> seedStagedSeasonality(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo) {
		var entity = new AlternativeIngredientSeasonalityWcEntity();
		entity.setAlternativeIngredientId(alternativeIngredientId);
		entity.setCountry(country);
		entity.setMonthFrom(monthFrom);
		entity.setMonthTo(monthTo);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStagedSeasonality(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<AlternativeIngredientSeasonalityWcEntity> cu = cb.createCriteriaUpdate(AlternativeIngredientSeasonalityWcEntity.class);
		Root<AlternativeIngredientSeasonalityWcEntity> wc = cu.getRoot();
		cu.set(wc.get(AlternativeIngredientSeasonalityWcEntity_.monthFrom), monthFrom);
		cu.set(wc.get(AlternativeIngredientSeasonalityWcEntity_.monthTo), monthTo);
		cu.set(wc.get(AlternativeIngredientSeasonalityWcEntity_.version), cb.sum(wc.get(AlternativeIngredientSeasonalityWcEntity_.version), 1L));
		cu.where(seasonalityRowAt(cb, wc, alternativeIngredientId, country, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientSeasonalityEntity.class, alternativeIngredientId)));
	}

	private Uni<Void> deleteStagedSeasonality(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<AlternativeIngredientSeasonalityWcEntity> cd = cb.createCriteriaDelete(AlternativeIngredientSeasonalityWcEntity.class);
		Root<AlternativeIngredientSeasonalityWcEntity> wc = cd.getRoot();
		cd.where(seasonalityRowAt(cb, wc, alternativeIngredientId, country, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientSeasonalityEntity.class, alternativeIngredientId)));
	}

	private static Predicate seasonalityRowAt(CriteriaBuilder cb, Root<AlternativeIngredientSeasonalityWcEntity> wc, UUID alternativeIngredientId, Country country, long baseVersion) {
		return cb.and(
				cb.equal(wc.get(AlternativeIngredientSeasonalityWcEntity_.alternativeIngredientId), alternativeIngredientId),
				cb.equal(wc.get(AlternativeIngredientSeasonalityWcEntity_.countryCode2), country.getCode2()),
				cb.equal(wc.get(AlternativeIngredientSeasonalityWcEntity_.version), baseVersion));
	}

	@Override
	public Uni<Void> stageCost(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Cost cost, long baseVersion) {
		return forc(
				tx.find(AlternativeIngredientCostWcEntity.class, new AlternativeIngredientCostWcEntityId(alternativeIngredientId, country)),
				_ -> tx.find(AlternativeIngredientCostEntity.class, new AlternativeIngredientCostEntityId(alternativeIngredientId, country)),
				(existing, master) -> applyCostEdit(tx, alternativeIngredientId, country, cost, baseVersion, existing, master)
		);
	}

	private Uni<Void> applyCostEdit(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Cost cost, long baseVersion, AlternativeIngredientCostWcEntity existing, AlternativeIngredientCostEntity master) {
		Cost masterCost = master == null ? null : master.getCost();
		boolean matchesMaster = Objects.equals(cost, masterCost);
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientCostEntity.class, alternativeIngredientId));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStagedCost(tx, alternativeIngredientId, country, cost);
		}
		return matchesMaster
				? deleteStagedCost(tx, alternativeIngredientId, country, baseVersion)
				: bumpStagedCost(tx, alternativeIngredientId, country, cost, baseVersion);
	}

	private Uni<Void> seedStagedCost(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Cost cost) {
		var entity = new AlternativeIngredientCostWcEntity();
		entity.setAlternativeIngredientId(alternativeIngredientId);
		entity.setCountry(country);
		entity.setCost(cost);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStagedCost(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Cost cost, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<AlternativeIngredientCostWcEntity> cu = cb.createCriteriaUpdate(AlternativeIngredientCostWcEntity.class);
		Root<AlternativeIngredientCostWcEntity> wc = cu.getRoot();
		cu.set(wc.get(AlternativeIngredientCostWcEntity_.cost), cost);
		cu.set(wc.get(AlternativeIngredientCostWcEntity_.version), cb.sum(wc.get(AlternativeIngredientCostWcEntity_.version), 1L));
		cu.where(costRowAt(cb, wc, alternativeIngredientId, country, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientCostEntity.class, alternativeIngredientId)));
	}

	private Uni<Void> deleteStagedCost(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<AlternativeIngredientCostWcEntity> cd = cb.createCriteriaDelete(AlternativeIngredientCostWcEntity.class);
		Root<AlternativeIngredientCostWcEntity> wc = cd.getRoot();
		cd.where(costRowAt(cb, wc, alternativeIngredientId, country, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(AlternativeIngredientCostEntity.class, alternativeIngredientId)));
	}

	private static Predicate costRowAt(CriteriaBuilder cb, Root<AlternativeIngredientCostWcEntity> wc, UUID alternativeIngredientId, Country country, long baseVersion) {
		return cb.and(
				cb.equal(wc.get(AlternativeIngredientCostWcEntity_.alternativeIngredientId), alternativeIngredientId),
				cb.equal(wc.get(AlternativeIngredientCostWcEntity_.countryCode2), country.getCode2()),
				cb.equal(wc.get(AlternativeIngredientCostWcEntity_.version), baseVersion));
	}

	private static AlternativeIngredientCountryKey keyOf(UUID alternativeIngredientId, String countryCode2) {
		return new AlternativeIngredientCountryKey(alternativeIngredientId, Country.fromCode2(countryCode2));
	}
}
