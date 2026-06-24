package eu.dietwise.dao.impl.recommendations;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.recommendations.AgeGroupEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationEntityId;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationWcEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationWcEntityId;
import eu.dietwise.dao.jpa.recommendations.RecommendationTranslationWcEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity_;
import eu.dietwise.dao.jpa.recommendations.RecommendationWcEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationWcEntity_;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.recommendations.ExplanationOverride;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;

/**
 * Implementation of the {@link RecommendationDao} with Hibernate Reactive.
 */
@ApplicationScoped
public class RecommendationDaoImpl implements RecommendationDao {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age, BiologicalGender gender) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		var ageGroup = recommendationValue.join(RecommendationValueEntity_.ageGroup);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), value));
		q.where(
				cb.and(
						cb.lessThanOrEqualTo(ageGroup.get(AgeGroupEntity_.min), age),
						cb.greaterThanOrEqualTo(ageGroup.get(AgeGroupEntity_.max), age),
						cb.equal(recommendationValue.get(RecommendationValueEntity_.gender), gender)
				)
		);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> tuple.get(value))));
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, BiologicalGender gender) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);
		Expression<Double> average = cb.avg(value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), average));
		q.where(cb.equal(recommendationValue.get(RecommendationValueEntity_.gender), gender))
				.groupBy(recommendation);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> BigDecimal.valueOf(tuple.get(average)))));
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		var ageGroup = recommendationValue.join(RecommendationValueEntity_.ageGroup);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);
		Expression<Double> average = cb.avg(value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), average));
		q.where(
				cb.and(
						cb.lessThanOrEqualTo(ageGroup.get(AgeGroupEntity_.min), age),
						cb.greaterThanOrEqualTo(ageGroup.get(AgeGroupEntity_.max), age)
				)
		);
		q.groupBy(recommendation);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> BigDecimal.valueOf(tuple.get(average)))));
	}

	@Override
	public Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendationValue = q.from(RecommendationValueEntity.class);
		var recommendation = recommendationValue.join(RecommendationValueEntity_.recommendation);
		Path<BigDecimal> value = recommendationValue.get(RecommendationValueEntity_.value);
		Expression<Double> average = cb.avg(value);

		q.select(cb.tuple(recommendation.get(RecommendationEntity_.name), average));
		q.groupBy(recommendation);

		return em.createQuery(q).getResultList()
				.map(values -> values.stream()
						.collect(Collectors.toMap(this::toRecommendation, tuple -> BigDecimal.valueOf(tuple.get(average)))));
	}

	@Override
	public Uni<List<RecommendationComponent>> listAllRecommendationsForScoring(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationEntity.class);
		q.from(RecommendationEntity.class);
		return em.createQuery(q).getResultList()
				.flatMap(values -> loadTranslationsByRecommendationId(em, lang)
						.map(translationsById -> values.stream()
								.map(value -> toRecommendationComponent(value, translationsById.get(value.getId())))
								.toList()));
	}

	@Override
	public Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createTupleQuery();
		var recommendation = q.from(RecommendationEntity.class);
		q.select(cb.tuple(recommendation.get(RecommendationEntity_.id), recommendation.get(RecommendationEntity_.name)));
		q.orderBy(cb.asc(recommendation.get(RecommendationEntity_.name)));
		return em.createQuery(q).getResultList()
				.map(rows -> rows.stream()
						.map(tuple -> new ReferenceOption(tuple.get(0, UUID.class), tuple.get(1, String.class)))
						.toList());
	}

	@Override
	public Uni<List<BackofficeRecommendation>> listForBackoffice(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationEntity.class);
		var recommendation = q.from(RecommendationEntity.class);
		q.select(recommendation).orderBy(cb.asc(recommendation.get(RecommendationEntity_.name)));
		return em.createQuery(q).getResultList()
				.map(list -> list.stream().map(RecommendationDaoImpl::toBackofficeRecommendation).toList());
	}

	@Override
	public Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em) {
		return forcm(
				masterTranslationLangs(em),
				_ -> stagedTranslationLangs(em),
				RecommendationDaoImpl::mergeTranslationLangs
		);
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> masterTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RecommendationTranslationEntity> t = q.from(RecommendationTranslationEntity.class);
		q.select(cb.tuple(
				t.get(RecommendationTranslationEntity_.recommendation).get(RecommendationEntity_.id),
				t.get(RecommendationTranslationEntity_.lang)
		)).where(cb.isNotNull(t.get(RecommendationTranslationEntity_.name)));
		return em.createQuery(q).getResultList().map(RecommendationDaoImpl::toLangsById);
	}

	private Uni<Map<UUID, Set<RecipeLanguage>>> stagedTranslationLangs(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RecommendationTranslationWcEntity> t = q.from(RecommendationTranslationWcEntity.class);
		q.select(cb.tuple(
				t.get(RecommendationTranslationWcEntity_.recommendationId),
				t.get(RecommendationTranslationWcEntity_.lang)
		));
		return em.createQuery(q).getResultList().map(RecommendationDaoImpl::toLangsById);
	}

	@Override
	public Uni<Map<UUID, ExplanationOverride>> findExplanationOverrides(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationWcEntity.class);
		q.from(RecommendationWcEntity.class);
		return em.createQuery(q).getResultList()
				.map(rows -> rows.stream().collect(Collectors.toMap(
						RecommendationWcEntity::getId,
						wc -> new ExplanationOverride(wc.getExplanationForLlm(), wc.getVersion()))));
	}

	@Override
	public Uni<Long> stageExplanation(ReactivePersistenceTxContext tx, UUID id, String explanationForLlm, long baseVersion) {
		return forc(
				tx.find(RecommendationWcEntity.class, id),
				_ -> tx.find(RecommendationEntity.class, id),
				(existing, master) -> applyEdit(tx, id, explanationForLlm, baseVersion, existing, master)
		);
	}

	private Uni<Long> applyEdit(ReactivePersistenceTxContext tx, UUID id, String explanationForLlm, long baseVersion, RecommendationWcEntity existing, RecommendationEntity master) {
		if (master == null) {
			return Uni.createFrom().failure(new EntityNotFoundException(RecommendationEntity.class, id));
		}
		boolean matchesMaster = Objects.equals(explanationForLlm, master.getExplanationForLlm());
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(RecommendationEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().item(0L) : seedStaged(tx, id, explanationForLlm);
		}
		return matchesMaster
				? deleteStaged(tx, id, baseVersion).replaceWith(0L)
				: bumpStaged(tx, id, explanationForLlm, baseVersion);
	}

	private Uni<Long> seedStaged(ReactivePersistenceTxContext tx, UUID id, String explanationForLlm) {
		var entity = new RecommendationWcEntity();
		entity.setId(id);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWith(1L);
	}

	private Uni<Long> bumpStaged(ReactivePersistenceTxContext tx, UUID id, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<RecommendationWcEntity> cu = cb.createCriteriaUpdate(RecommendationWcEntity.class);
		Root<RecommendationWcEntity> wc = cu.getRoot();
		cu.set(wc.get(RecommendationWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(RecommendationWcEntity_.version), cb.sum(wc.get(RecommendationWcEntity_.version), 1L));
		cu.where(cb.and(
				cb.equal(wc.get(RecommendationWcEntity_.id), id),
				cb.equal(wc.get(RecommendationWcEntity_.version), baseVersion)
		));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().item(baseVersion + 1)
				: Uni.createFrom().failure(new StaleVersionException(RecommendationEntity.class, id)));
	}

	@Override
	public Uni<Void> revertExplanation(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		return tx.find(RecommendationWcEntity.class, id).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: deleteStaged(tx, id, baseVersion));
	}

	private Uni<Void> deleteStaged(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<RecommendationWcEntity> cd = cb.createCriteriaDelete(RecommendationWcEntity.class);
		Root<RecommendationWcEntity> wc = cd.getRoot();
		cd.where(cb.and(
				cb.equal(wc.get(RecommendationWcEntity_.id), id),
				cb.equal(wc.get(RecommendationWcEntity_.version), baseVersion)
		));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RecommendationEntity.class, id)));
	}

	@Override
	public Uni<Map<RecipeLanguage, RecommendationTranslationDetails>> findTranslationsForEdit(ReactivePersistenceContext em, UUID id) {
		return masterTranslationsForRecommendation(em, id).flatMap(master ->
				stagedTranslationsForRecommendation(em, id).map(staged -> toEditableTranslations(master, staged)));
	}

	private Uni<Map<RecipeLanguage, RecommendationTranslationEntity>> masterTranslationsForRecommendation(ReactivePersistenceContext em, UUID id) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationTranslationEntity.class);
		Root<RecommendationTranslationEntity> t = q.from(RecommendationTranslationEntity.class);
		q.select(t).where(cb.equal(t.get(RecommendationTranslationEntity_.recommendation).get(RecommendationEntity_.id), id));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(Collectors.toMap(RecommendationTranslationEntity::getLang, m -> m)));
	}

	private Uni<Map<RecipeLanguage, RecommendationTranslationWcEntity>> stagedTranslationsForRecommendation(ReactivePersistenceContext em, UUID id) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationTranslationWcEntity.class);
		Root<RecommendationTranslationWcEntity> t = q.from(RecommendationTranslationWcEntity.class);
		q.select(t).where(cb.equal(t.get(RecommendationTranslationWcEntity_.recommendationId), id));
		return em.createQuery(q).getResultList().map(list -> list.stream()
				.collect(Collectors.toMap(RecommendationTranslationWcEntity::getLang, w -> w)));
	}

	private static Map<RecipeLanguage, RecommendationTranslationDetails> toEditableTranslations(
			Map<RecipeLanguage, RecommendationTranslationEntity> master,
			Map<RecipeLanguage, RecommendationTranslationWcEntity> staged
	) {
		Map<RecipeLanguage, RecommendationTranslationDetails> result = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			RecommendationTranslationEntity m = master.get(lang);
			RecommendationTranslationWcEntity wc = staged.get(lang);
			if (wc != null) {
				result.put(lang, new RecommendationTranslationDetails(wc.getName(), wc.getComponentForScoring(), wc.getExplanationForLlm(), wc.getVersion()));
			} else if (m != null) {
				result.put(lang, new RecommendationTranslationDetails(m.getName(), m.getComponentForScoring(), m.getExplanationForLlm(), 0L));
			} else {
				result.put(lang, new RecommendationTranslationDetails(null, null, null, 0L));
			}
		}
		return result;
	}

	@Override
	public Uni<Void> stageTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm, long baseVersion) {
		return forc(
				tx.find(RecommendationTranslationWcEntity.class, new RecommendationTranslationWcEntityId(id, lang)),
				_ -> tx.find(RecommendationTranslationEntity.class, new RecommendationTranslationEntityId(id, lang)),
				(existing, master) -> applyTranslationEdit(tx, id, lang, name, componentForScoring, explanationForLlm, baseVersion, existing, master)
		);
	}

	private Uni<Void> applyTranslationEdit(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm, long baseVersion, RecommendationTranslationWcEntity existing, RecommendationTranslationEntity master) {
		String masterName = master == null ? null : master.getName();
		String masterComponent = master == null ? null : master.getComponentForScoring();
		String masterExplanation = master == null ? null : master.getExplanationForLlm();
		boolean matchesMaster = Objects.equals(name, masterName)
				&& Objects.equals(componentForScoring, masterComponent)
				&& Objects.equals(explanationForLlm, masterExplanation);
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(RecommendationTranslationEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStagedTranslation(tx, id, lang, name, componentForScoring, explanationForLlm);
		}
		return matchesMaster
				? deleteStagedTranslation(tx, id, lang, baseVersion)
				: bumpStagedTranslation(tx, id, lang, name, componentForScoring, explanationForLlm, baseVersion);
	}

	private Uni<Void> seedStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm) {
		var entity = new RecommendationTranslationWcEntity();
		entity.setRecommendationId(id);
		entity.setLang(lang);
		entity.setName(name);
		entity.setComponentForScoring(componentForScoring);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<RecommendationTranslationWcEntity> cu = cb.createCriteriaUpdate(RecommendationTranslationWcEntity.class);
		Root<RecommendationTranslationWcEntity> wc = cu.getRoot();
		cu.set(wc.get(RecommendationTranslationWcEntity_.name), name);
		cu.set(wc.get(RecommendationTranslationWcEntity_.componentForScoring), componentForScoring);
		cu.set(wc.get(RecommendationTranslationWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(RecommendationTranslationWcEntity_.version), cb.sum(wc.get(RecommendationTranslationWcEntity_.version), 1L));
		cu.where(translationRowAt(cb, wc, id, lang, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RecommendationTranslationEntity.class, id)));
	}

	@Override
	public Uni<Void> revertTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion) {
		return tx.find(RecommendationTranslationWcEntity.class, new RecommendationTranslationWcEntityId(id, lang)).flatMap(existing -> existing == null
				? Uni.createFrom().voidItem()
				: deleteStagedTranslation(tx, id, lang, baseVersion));
	}

	private Uni<Void> deleteStagedTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<RecommendationTranslationWcEntity> cd = cb.createCriteriaDelete(RecommendationTranslationWcEntity.class);
		Root<RecommendationTranslationWcEntity> wc = cd.getRoot();
		cd.where(translationRowAt(cb, wc, id, lang, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rows -> rows == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RecommendationTranslationEntity.class, id)));
	}

	private static Predicate translationRowAt(CriteriaBuilder cb, Root<RecommendationTranslationWcEntity> wc, UUID id, RecipeLanguage lang, long baseVersion) {
		return cb.and(
				cb.equal(wc.get(RecommendationTranslationWcEntity_.recommendationId), id),
				cb.equal(wc.get(RecommendationTranslationWcEntity_.lang), lang),
				cb.equal(wc.get(RecommendationTranslationWcEntity_.version), baseVersion));
	}

	private static Map<UUID, Set<RecipeLanguage>> toLangsById(List<Tuple> rows) {
		Map<UUID, Set<RecipeLanguage>> byId = new HashMap<>();
		for (Tuple row : rows) {
			byId.computeIfAbsent(row.get(0, UUID.class), _ -> EnumSet.noneOf(RecipeLanguage.class))
					.add(row.get(1, RecipeLanguage.class));
		}
		return byId;
	}

	private static Map<UUID, TranslationLangs> mergeTranslationLangs(Map<UUID, Set<RecipeLanguage>> master, Map<UUID, Set<RecipeLanguage>> staged) {
		Set<UUID> ids = new HashSet<>(master.keySet());
		ids.addAll(staged.keySet());
		Map<UUID, TranslationLangs> result = new HashMap<>();
		for (UUID id : ids) {
			result.put(id, new TranslationLangs(
					master.getOrDefault(id, EnumSet.noneOf(RecipeLanguage.class)),
					staged.getOrDefault(id, EnumSet.noneOf(RecipeLanguage.class))));
		}
		return result;
	}

	private static BackofficeRecommendation toBackofficeRecommendation(RecommendationEntity e) {
		return new BackofficeRecommendation(e.getId(), e.getName(), e.getComponentForScoring(), e.getWeight(), e.getExplanationForLlm());
	}

	private Uni<Map<UUID, RecommendationTranslationEntity>> loadTranslationsByRecommendationId(
			ReactivePersistenceContext em,
			RecipeLanguage lang
	) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RecommendationTranslationEntity.class);
		var translation = q.from(RecommendationTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(RecommendationTranslationEntity_.lang), lang));
		return em.createQuery(q).getResultList()
				.map(values -> values.stream().collect(Collectors.toMap(
						t -> t.getRecommendation().getId(),
						t -> t
				)));
	}

	private Recommendation toRecommendation(Tuple tuple) {
		return new RecommendationImpl(tuple.get(0, String.class));
	}

	private RecommendationComponent toRecommendationComponent(RecommendationEntity e, RecommendationTranslationEntity t) {
		return ImmutableRecommendationComponent.builder()
				.recommendation(new RecommendationImpl(t != null && t.getName() != null ? t.getName() : e.getName()))
				.componentForScoring(new RecommendationComponentNameImpl(
						t != null && t.getComponentForScoring() != null ? t.getComponentForScoring() : e.getComponentForScoring()))
				.weight(e.getWeight())
				.explanationForLlm(Optional.ofNullable(t != null && t.getExplanationForLlm() != null ? t.getExplanationForLlm() : e.getExplanationForLlm()))
				.build();
	}
}
