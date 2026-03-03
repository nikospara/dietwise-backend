package eu.dietwise.dao.impl.suggestions;

import java.util.List;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.services.model.suggestions.ImmutableRoleOrTechnique;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RoleOrTechniqueDaoImpl implements RoleOrTechniqueDao {
	@Override
	public Uni<List<RoleOrTechnique>> findAll(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(RoleOrTechniqueEntity.class);
		Root<RoleOrTechniqueEntity> roleOrTechnique = q.from(RoleOrTechniqueEntity.class);
		q.select(roleOrTechnique);
		return em.createQuery(q).getResultList().map(RoleOrTechniqueDaoImpl::toRoleOrTechniqueList);
	}

	private static List<RoleOrTechnique> toRoleOrTechniqueList(List<RoleOrTechniqueEntity> list) {
		return list.stream().map(RoleOrTechniqueDaoImpl::toRoleOrTechnique).toList();
	}

	private static RoleOrTechnique toRoleOrTechnique(RoleOrTechniqueEntity e) {
		return ImmutableRoleOrTechnique.builder()
				.id(new UuidRoleOrTechniqueId(e.getId()))
				.name(e.getName())
				.explanationForLlm(Optional.ofNullable(e.getExplanationForLlm()))
				.build();
	}
}
