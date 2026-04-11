package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueTranslationEntity_;
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
