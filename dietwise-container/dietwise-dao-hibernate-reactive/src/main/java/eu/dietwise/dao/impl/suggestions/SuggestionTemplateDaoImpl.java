package eu.dietwise.dao.impl.suggestions;

import static java.util.stream.Collectors.toMap;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateWcEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateWcEntity_;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionTemplateDaoImpl implements SuggestionTemplateDao {
	@Override
	public Uni<List<SuggestionTemplate>> findByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateEntity.class);
		Root<SuggestionTemplateEntity> suggestionTemplate = q.from(SuggestionTemplateEntity.class);
		suggestionTemplate.fetch(SuggestionTemplateEntity_.alternativeIngredient);
		q.select(suggestionTemplate)
				.where(cb.equal(suggestionTemplate.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId))
				.orderBy(cb.asc(suggestionTemplate.get(SuggestionTemplateEntity_.alternativeOrder)));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toSuggestionTemplates);
	}

	@Override
	public Uni<Map<UUID, StagedSuggestionTemplateOverlay>> findStagedOverlayByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateWcEntity.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		q.select(wc).where(cb.equal(wc.get(SuggestionTemplateWcEntity_.ruleId), ruleId));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toOverlayById);
	}

	@Override
	public Uni<Set<UUID>> findRuleIdsWithStagedTemplates(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		q.select(wc.get(SuggestionTemplateWcEntity_.ruleId)).distinct(true);
		return em.createQuery(q).getResultList().map(HashSet::new);
	}

	@Override
	public Uni<Long> stageField(ReactivePersistenceTxContext tx, UUID templateId, SuggestionTemplateField field, String value, long baseVersion) {
		return tx.find(SuggestionTemplateWcEntity.class, templateId).flatMap(existing -> {
			if (existing != null) {
				return bumpStagedField(tx, templateId, column(field), value, baseVersion);
			}
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateEntity.class, templateId));
			}
			return seedStagedField(tx, templateId, field, value);
		});
	}

	@Override
	public Uni<Void> revertField(ReactivePersistenceTxContext tx, UUID templateId, SuggestionTemplateField field, long baseVersion) {
		return tx.find(SuggestionTemplateWcEntity.class, templateId).flatMap(existing -> {
			if (existing == null) {
				return Uni.createFrom().voidItem();
			}
			return tx.find(SuggestionTemplateEntity.class, templateId).flatMap(master -> master == null || collapsesAfterRevert(existing, field, master)
					? deleteStagedRow(tx, templateId, baseVersion)
					: bumpStagedField(tx, templateId, column(field), masterValue(master, field), baseVersion).replaceWithVoid());
		});
	}

	private Uni<Long> seedStagedField(ReactivePersistenceTxContext tx, UUID templateId, SuggestionTemplateField field, String value) {
		return tx.find(SuggestionTemplateEntity.class, templateId).flatMap(master -> master == null
				? Uni.createFrom().failure(new EntityNotFoundException(SuggestionTemplateEntity.class, templateId))
				: seedSnapshot(tx, master, wc -> setField(wc, field, value)).replaceWith(1L));
	}

	private Uni<SuggestionTemplateWcEntity> seedSnapshot(ReactivePersistenceTxContext tx, SuggestionTemplateEntity master, Consumer<SuggestionTemplateWcEntity> override) {
		var wc = new SuggestionTemplateWcEntity();
		wc.setId(master.getId());
		wc.setRuleId(master.getRule().getId());
		wc.setAlternativeIngredientId(master.getAlternativeIngredient().getId());
		wc.setAlternativeOrder(master.getAlternativeOrder());
		wc.setRestriction(master.getRestriction());
		wc.setEquivalence(master.getEquivalence());
		wc.setTechniqueNotes(master.getTechniqueNotes());
		wc.setVersion(1L);
		override.accept(wc);
		return tx.persist(wc).replaceWith(wc);
	}

	private Uni<Long> bumpStagedField(ReactivePersistenceTxContext tx, UUID templateId, SingularAttribute<SuggestionTemplateWcEntity, String> field, String value, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<SuggestionTemplateWcEntity> cu = cb.createCriteriaUpdate(SuggestionTemplateWcEntity.class);
		Root<SuggestionTemplateWcEntity> wc = cu.getRoot();
		cu.set(wc.get(field), value);
		cu.set(wc.get(SuggestionTemplateWcEntity_.version), cb.sum(wc.get(SuggestionTemplateWcEntity_.version), 1L));
		cu.where(cb.and(
				cb.equal(wc.get(SuggestionTemplateWcEntity_.id), templateId),
				cb.equal(wc.get(SuggestionTemplateWcEntity_.version), baseVersion)
		));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().item(baseVersion + 1)
				: Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateEntity.class, templateId)));
	}

	private Uni<Void> deleteStagedRow(ReactivePersistenceTxContext tx, UUID templateId, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<SuggestionTemplateWcEntity> cd = cb.createCriteriaDelete(SuggestionTemplateWcEntity.class);
		Root<SuggestionTemplateWcEntity> wc = cd.getRoot();
		cd.where(cb.and(
				cb.equal(wc.get(SuggestionTemplateWcEntity_.id), templateId),
				cb.equal(wc.get(SuggestionTemplateWcEntity_.version), baseVersion)
		));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateEntity.class, templateId)));
	}

	private static boolean collapsesAfterRevert(SuggestionTemplateWcEntity existing, SuggestionTemplateField reverted, SuggestionTemplateEntity master) {
		String restriction = reverted == SuggestionTemplateField.RESTRICTION ? master.getRestriction() : existing.getRestriction();
		String equivalence = reverted == SuggestionTemplateField.EQUIVALENCE ? master.getEquivalence() : existing.getEquivalence();
		String techniqueNotes = reverted == SuggestionTemplateField.TECHNIQUE_NOTES ? master.getTechniqueNotes() : existing.getTechniqueNotes();
		return Objects.equals(restriction, master.getRestriction())
				&& Objects.equals(equivalence, master.getEquivalence())
				&& Objects.equals(techniqueNotes, master.getTechniqueNotes());
	}

	private static SingularAttribute<SuggestionTemplateWcEntity, String> column(SuggestionTemplateField field) {
		return switch (field) {
			case RESTRICTION -> SuggestionTemplateWcEntity_.restriction;
			case EQUIVALENCE -> SuggestionTemplateWcEntity_.equivalence;
			case TECHNIQUE_NOTES -> SuggestionTemplateWcEntity_.techniqueNotes;
		};
	}

	private static String masterValue(SuggestionTemplateEntity master, SuggestionTemplateField field) {
		return switch (field) {
			case RESTRICTION -> master.getRestriction();
			case EQUIVALENCE -> master.getEquivalence();
			case TECHNIQUE_NOTES -> master.getTechniqueNotes();
		};
	}

	private static void setField(SuggestionTemplateWcEntity wc, SuggestionTemplateField field, String value) {
		switch (field) {
			case RESTRICTION -> wc.setRestriction(value);
			case EQUIVALENCE -> wc.setEquivalence(value);
			case TECHNIQUE_NOTES -> wc.setTechniqueNotes(value);
		}
	}

	private static Map<UUID, StagedSuggestionTemplateOverlay> toOverlayById(List<SuggestionTemplateWcEntity> rows) {
		return rows.stream().collect(toMap(
				SuggestionTemplateWcEntity::getId,
				row -> new StagedSuggestionTemplateOverlay(row.getRestriction(), row.getEquivalence(), row.getTechniqueNotes(), row.getVersion()),
				(existing, ignored) -> existing,
				LinkedHashMap::new
		));
	}

	private static List<SuggestionTemplate> toSuggestionTemplates(List<SuggestionTemplateEntity> entities) {
		return entities.stream().map(SuggestionTemplateDaoImpl::toSuggestionTemplate).toList();
	}

	private static SuggestionTemplate toSuggestionTemplate(SuggestionTemplateEntity e) {
		return ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(e.getId().toString()))
				.alternative(new AlternativeIngredientImpl(e.getAlternativeIngredient().getName()))
				.restriction(Optional.ofNullable(e.getRestriction()))
				.equivalence(Optional.ofNullable(e.getEquivalence()))
				.techniqueNotes(Optional.ofNullable(e.getTechniqueNotes()))
				.build();
	}
}
