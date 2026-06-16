package eu.dietwise.dao.impl.suggestions;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceDetails;
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

	@Override
	public Uni<Map<UUID, String>> findStagedNames(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<RoleOrTechniqueWcEntity> root = q.from(RoleOrTechniqueWcEntity.class);
		q.select(cb.tuple(root.get(RoleOrTechniqueWcEntity_.id), root.get(RoleOrTechniqueWcEntity_.name)));
		return em.createQuery(q).getResultList().map(rows -> rows.stream()
				.collect(java.util.stream.Collectors.toMap(t -> t.get(0, UUID.class), t -> t.get(1, String.class))));
	}

	@Override
	public Uni<ReferenceDetails> findEditableById(ReactivePersistenceContext em, UUID id) {
		return em.find(RoleOrTechniqueWcEntity.class, id).flatMap(mirror -> mirror != null
				? Uni.createFrom().item(new ReferenceDetails(mirror.getName(), mirror.getExplanationForLlm(), mirror.getVersion()))
				: em.find(RoleOrTechniqueEntity.class, id).map(master -> {
					if (master == null) {
						throw new EntityNotFoundException(RoleOrTechniqueEntity.class, id);
					}
					return new ReferenceDetails(master.getName(), master.getExplanationForLlm(), 0L);
				}));
	}

	@Override
	public Uni<Void> editRoleOrTechnique(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		return tx.find(RoleOrTechniqueWcEntity.class, id).flatMap(existing ->
				tx.find(RoleOrTechniqueEntity.class, id).flatMap(master -> applyEdit(tx, id, name, explanationForLlm, baseVersion, existing, master)));
	}

	private Uni<Void> applyEdit(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion, RoleOrTechniqueWcEntity existing, RoleOrTechniqueEntity master) {
		if (existing == null && master == null) {
			return Uni.createFrom().failure(new EntityNotFoundException(RoleOrTechniqueEntity.class, id));
		}
		boolean matchesMaster = master != null
				&& Objects.equals(name, master.getName())
				&& Objects.equals(explanationForLlm, master.getExplanationForLlm());
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(RoleOrTechniqueEntity.class, id));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedStaged(tx, id, name, explanationForLlm);
		}
		return matchesMaster
				? deleteStaged(tx, id, baseVersion)
				: bumpStaged(tx, id, name, explanationForLlm, baseVersion);
	}

	private Uni<Void> seedStaged(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm) {
		var entity = new RoleOrTechniqueWcEntity();
		entity.setId(id);
		entity.setName(name);
		entity.setExplanationForLlm(explanationForLlm);
		entity.setVersion(1L);
		return tx.persist(entity).replaceWithVoid();
	}

	private Uni<Void> bumpStaged(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<RoleOrTechniqueWcEntity> cu = cb.createCriteriaUpdate(RoleOrTechniqueWcEntity.class);
		Root<RoleOrTechniqueWcEntity> wc = cu.getRoot();
		cu.set(wc.get(RoleOrTechniqueWcEntity_.name), name);
		cu.set(wc.get(RoleOrTechniqueWcEntity_.explanationForLlm), explanationForLlm);
		cu.set(wc.get(RoleOrTechniqueWcEntity_.version), cb.sum(wc.get(RoleOrTechniqueWcEntity_.version), 1L));
		cu.where(cb.and(
				cb.equal(wc.get(RoleOrTechniqueWcEntity_.id), id),
				cb.equal(wc.get(RoleOrTechniqueWcEntity_.version), baseVersion)
		));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RoleOrTechniqueEntity.class, id)));
	}

	private Uni<Void> deleteStaged(ReactivePersistenceTxContext tx, UUID id, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<RoleOrTechniqueWcEntity> cd = cb.createCriteriaDelete(RoleOrTechniqueWcEntity.class);
		Root<RoleOrTechniqueWcEntity> wc = cd.getRoot();
		cd.where(cb.and(
				cb.equal(wc.get(RoleOrTechniqueWcEntity_.id), id),
				cb.equal(wc.get(RoleOrTechniqueWcEntity_.version), baseVersion)
		));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(RoleOrTechniqueEntity.class, id)));
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
