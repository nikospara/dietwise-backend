package eu.dietwise.dao.impl.suggestions;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity_;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.ImmutableAlternativeIngredient;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Seasonality;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class AlternativeIngredientDaoImpl implements AlternativeIngredientDao {
	@Override
	public Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientEntity.class);
		Root<AlternativeIngredientEntity> alternativeIngredient = q.from(AlternativeIngredientEntity.class);
		alternativeIngredient.fetch(AlternativeIngredientEntity_.seasonalityByCountry, JoinType.LEFT);
		q.select(alternativeIngredient);
		q.distinct(true);
		return em.createQuery(q).getResultList()
				.flatMap(list -> loadTranslationsByAlternativeIngredientId(em, lang)
						.map(translationsById -> toAlternativeIngredientList(list, translationsById)));
	}

	@Override
	public Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em) {
		return masterOptions(em).flatMap(master -> mirrorOptions(em).map(mirror -> mergeOptions(master, mirror)));
	}

	@Override
	public Uni<UUID> createAlternativeIngredient(ReactivePersistenceTxContext tx, String name) {
		var entity = new AlternativeIngredientWcEntity();
		UUID id = UUID.randomUUID();
		entity.setId(id);
		entity.setName(name);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWith(id);
	}

	private Uni<List<ReferenceOption>> masterOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientEntity> root = q.from(AlternativeIngredientEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientEntity_.id), root.get(AlternativeIngredientEntity_.name)));
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toOptions);
	}

	private Uni<List<ReferenceOption>> mirrorOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientWcEntity> root = q.from(AlternativeIngredientWcEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientWcEntity_.id), root.get(AlternativeIngredientWcEntity_.name)));
		return em.createQuery(q).getResultList().map(AlternativeIngredientDaoImpl::toOptions);
	}

	private static List<ReferenceOption> toOptions(List<Tuple> rows) {
		return rows.stream().map(t -> new ReferenceOption(t.get(0, UUID.class), t.get(1, String.class))).toList();
	}

	private static List<ReferenceOption> mergeOptions(List<ReferenceOption> master, List<ReferenceOption> mirror) {
		Map<UUID, ReferenceOption> byId = new LinkedHashMap<>();
		master.forEach(option -> byId.put(option.id(), option));
		mirror.forEach(option -> byId.put(option.id(), option));
		return byId.values().stream().sorted(Comparator.comparing(ReferenceOption::name)).toList();
	}

	private Uni<Map<UUID, AlternativeIngredientTranslationEntity>> loadTranslationsByAlternativeIngredientId(
			ReactivePersistenceContext em,
			RecipeLanguage lang
	) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(AlternativeIngredientTranslationEntity.class);
		var translation = q.from(AlternativeIngredientTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(AlternativeIngredientTranslationEntity_.lang), lang));
		return em.createQuery(q)
				.getResultList()
				.map(list -> list.stream().collect(Collectors.toMap(
						t -> t.getAlternativeIngredient().getId(),
						t -> t
				)));
	}

	private static List<AlternativeIngredient> toAlternativeIngredientList(
			List<AlternativeIngredientEntity> list,
			Map<UUID, AlternativeIngredientTranslationEntity> translationsById
	) {
		return list.stream().map(e -> toAlternativeIngredient(e, translationsById.get(e.getId()))).toList();
	}

	private static AlternativeIngredient toAlternativeIngredient(AlternativeIngredientEntity e, AlternativeIngredientTranslationEntity t) {
		return ImmutableAlternativeIngredient.builder()
				.id(new UuidAlternativeIngredientId(e.getId()))
				.name(t != null && t.getName() != null ? t.getName() : e.getName())
				.explanationForLlm(Optional.ofNullable(t != null && t.getExplanationForLlm() != null ? t.getExplanationForLlm() : e.getExplanationForLlm()))
				.seasonalityByCountry(toSeasonalityByCountry(e))
				.build();
	}

	private static Optional<Map<Country, Seasonality>> toSeasonalityByCountry(AlternativeIngredientEntity e) {
		if (e.getSeasonalityByCountry() == null || e.getSeasonalityByCountry().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(e.getSeasonalityByCountry().stream().collect(Collectors.toUnmodifiableMap(
				AlternativeIngredientSeasonalityEntity::getCountry,
				s -> ImmutableSeasonality.builder().monthFrom(s.getMonthFrom()).monthTo(s.getMonthTo()).build()
		)));
	}
}
