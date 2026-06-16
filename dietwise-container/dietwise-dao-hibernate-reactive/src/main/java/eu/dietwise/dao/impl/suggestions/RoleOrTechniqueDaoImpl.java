package eu.dietwise.dao.impl.suggestions;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity_;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueWcEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueWcEntity_;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.services.model.suggestions.ImmutableRoleOrTechnique;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RoleOrTechniqueDaoImpl implements RoleOrTechniqueDao {
	@Override
	public Uni<List<RoleOrTechnique>> findAll(ReactivePersistenceContext em, RecipeLanguage lang) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RoleOrTechniqueEntity.class);
		Root<RoleOrTechniqueEntity> roleOrTechnique = q.from(RoleOrTechniqueEntity.class);
		q.select(roleOrTechnique);
		return em.createQuery(q).getResultList()
				.flatMap(list -> loadTranslationsByRoleOrTechniqueId(em, lang)
						.map(translationsById -> toRoleOrTechniqueList(list, translationsById)));
	}

	@Override
	public Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em) {
		return masterOptions(em).flatMap(master -> mirrorOptions(em).map(mirror -> mergeOptions(master, mirror)));
	}

	@Override
	public Uni<UUID> createRoleOrTechnique(ReactivePersistenceTxContext tx, String name) {
		var entity = new RoleOrTechniqueWcEntity();
		UUID id = UUID.randomUUID();
		entity.setId(id);
		entity.setName(name);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWith(id);
	}

	private Uni<List<ReferenceOption>> masterOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RoleOrTechniqueEntity> root = q.from(RoleOrTechniqueEntity.class);
		q.select(cb.tuple(root.get(RoleOrTechniqueEntity_.id), root.get(RoleOrTechniqueEntity_.name)));
		return em.createQuery(q).getResultList().map(RoleOrTechniqueDaoImpl::toOptions);
	}

	private Uni<List<ReferenceOption>> mirrorOptions(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RoleOrTechniqueWcEntity> root = q.from(RoleOrTechniqueWcEntity.class);
		q.select(cb.tuple(root.get(RoleOrTechniqueWcEntity_.id), root.get(RoleOrTechniqueWcEntity_.name)));
		return em.createQuery(q).getResultList().map(RoleOrTechniqueDaoImpl::toOptions);
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

	private Uni<Map<UUID, RoleOrTechniqueTranslationEntity>> loadTranslationsByRoleOrTechniqueId(
			ReactivePersistenceContext em,
			RecipeLanguage lang
	) {
		if (lang == RecipeLanguage.EN) {
			return Uni.createFrom().item(Map.of());
		}
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RoleOrTechniqueTranslationEntity.class);
		var translation = q.from(RoleOrTechniqueTranslationEntity.class);
		q.select(translation).where(cb.equal(translation.get(RoleOrTechniqueTranslationEntity_.lang), lang));
		return em.createQuery(q)
				.getResultList()
				.map(list -> list.stream().collect(java.util.stream.Collectors.toMap(
						t -> t.getRoleOrTechnique().getId(),
						t -> t
				)));
	}

	private static List<RoleOrTechnique> toRoleOrTechniqueList(
			List<RoleOrTechniqueEntity> list,
			Map<UUID, RoleOrTechniqueTranslationEntity> translationsById
	) {
		return list.stream().map(e -> toRoleOrTechnique(e, translationsById.get(e.getId()))).toList();
	}

	private static RoleOrTechnique toRoleOrTechnique(RoleOrTechniqueEntity e, RoleOrTechniqueTranslationEntity t) {
		return ImmutableRoleOrTechnique.builder()
				.id(new UuidRoleOrTechniqueId(e.getId()))
				.name(t != null && t.getName() != null ? t.getName() : e.getName())
				.explanationForLlm(Optional.ofNullable(t != null && t.getExplanationForLlm() != null ? t.getExplanationForLlm() : e.getExplanationForLlm()))
				.build();
	}
}
