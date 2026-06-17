package eu.dietwise.dao.impl.suggestions;

import static java.util.stream.Collectors.toMap;
import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
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
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.SingularAttribute;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationWcEntity_;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity_;
import eu.dietwise.dao.jpa.suggestions.RuleEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationEntityId;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationWcEntityId;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationWcEntity_;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateWcEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateWcEntity_;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.services.model.suggestions.FieldTranslationLangs;
import eu.dietwise.services.model.suggestions.NewSuggestionTemplate;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SuggestionTemplateDaoImpl implements SuggestionTemplateDao {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

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
	public Uni<List<NewSuggestionTemplate>> findNewByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<SuggestionTemplateWcEntity> q = cb.createQuery(SuggestionTemplateWcEntity.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		Subquery<UUID> master = q.subquery(UUID.class);
		Root<SuggestionTemplateEntity> masterTemplate = master.from(SuggestionTemplateEntity.class);
		master.select(masterTemplate.get(SuggestionTemplateEntity_.id))
				.where(cb.equal(masterTemplate.get(SuggestionTemplateEntity_.id), wc.get(SuggestionTemplateWcEntity_.id)));
		q.select(wc)
				.where(cb.and(cb.equal(wc.get(SuggestionTemplateWcEntity_.ruleId), ruleId), cb.not(cb.exists(master))))
				.orderBy(cb.asc(wc.get(SuggestionTemplateWcEntity_.alternativeOrder)));
		return em.createQuery(q).getResultList().flatMap(rows -> resolveNewTemplates(em, rows));
	}

	private Uni<List<NewSuggestionTemplate>> resolveNewTemplates(ReactivePersistenceContext em, List<SuggestionTemplateWcEntity> rows) {
		if (rows.isEmpty()) {
			return Uni.createFrom().item(List.of());
		}
		return alternativeNamesById(em).map(names -> rows.stream().map(row -> toNewSuggestionTemplate(row, names)).toList());
	}

	private Uni<Map<UUID, String>> alternativeNamesById(ReactivePersistenceContext em) {
		return masterAlternativeNames(em).flatMap(master ->
				stagedAlternativeNames(em).map(staged -> {
					Map<UUID, String> merged = new HashMap<>(master);
					merged.putAll(staged);
					return merged;
				}));
	}

	private Uni<Map<UUID, String>> masterAlternativeNames(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientEntity> root = q.from(AlternativeIngredientEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientEntity_.id), root.get(AlternativeIngredientEntity_.name)));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toNameMap);
	}

	private Uni<Map<UUID, String>> stagedAlternativeNames(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<AlternativeIngredientWcEntity> root = q.from(AlternativeIngredientWcEntity.class);
		q.select(cb.tuple(root.get(AlternativeIngredientWcEntity_.id), root.get(AlternativeIngredientWcEntity_.name)));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toNameMap);
	}

	private static Map<UUID, String> toNameMap(List<Tuple> rows) {
		return rows.stream().collect(toMap(t -> t.get(0, UUID.class), t -> t.get(1, String.class)));
	}

	private static NewSuggestionTemplate toNewSuggestionTemplate(SuggestionTemplateWcEntity row, Map<UUID, String> alternativeNames) {
		SuggestionTemplate template = ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(row.getId().toString()))
				.alternative(new AlternativeIngredientImpl(alternativeNames.get(row.getAlternativeIngredientId())))
				.restriction(Optional.ofNullable(row.getRestriction()))
				.equivalence(Optional.ofNullable(row.getEquivalence()))
				.techniqueNotes(Optional.ofNullable(row.getTechniqueNotes()))
				.build();
		return new NewSuggestionTemplate(template, row.getVersion());
	}

	@Override
	public Uni<Optional<UUID>> findTemplateIdByRuleAndAlternative(ReactivePersistenceContext em, UUID ruleId, UUID alternativeIngredientId) {
		return masterTemplateId(em, ruleId, alternativeIngredientId).flatMap(masterId -> masterId.isPresent()
				? Uni.createFrom().item(masterId)
				: wcTemplateId(em, ruleId, alternativeIngredientId));
	}

	private Uni<Optional<UUID>> masterTemplateId(ReactivePersistenceContext em, UUID ruleId, UUID alternativeIngredientId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		q.select(st.get(SuggestionTemplateEntity_.id)).where(cb.and(
				cb.equal(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId),
				cb.equal(st.get(SuggestionTemplateEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id), alternativeIngredientId)));
		return em.createQuery(q).getResultList().map(ids -> ids.stream().findFirst());
	}

	private Uni<Optional<UUID>> wcTemplateId(ReactivePersistenceContext em, UUID ruleId, UUID alternativeIngredientId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		q.select(wc.get(SuggestionTemplateWcEntity_.id)).where(cb.and(
				cb.equal(wc.get(SuggestionTemplateWcEntity_.ruleId), ruleId),
				cb.equal(wc.get(SuggestionTemplateWcEntity_.alternativeIngredientId), alternativeIngredientId)));
		return em.createQuery(q).getResultList().map(ids -> ids.stream().findFirst());
	}

	@Override
	public Uni<UUID> addTemplate(ReactivePersistenceTxContext tx, UUID ruleId, UUID alternativeIngredientId) {
		return nextAlternativeOrder(tx, ruleId).flatMap(order -> {
			var wc = new SuggestionTemplateWcEntity();
			UUID id = UUID.randomUUID();
			wc.setId(id);
			wc.setRuleId(ruleId);
			wc.setAlternativeIngredientId(alternativeIngredientId);
			wc.setAlternativeOrder(order);
			wc.setActive(true);
			wc.setVersion(1L);
			return tx.persist(wc).replaceWith(id);
		});
	}

	private Uni<Integer> nextAlternativeOrder(ReactivePersistenceTxContext tx, UUID ruleId) {
		return maxMasterOrder(tx, ruleId)
				.flatMap(masterMax -> maxWcOrder(tx, ruleId).map(wcMax -> Math.max(masterMax, wcMax) + 1));
	}

	private Uni<Integer> maxMasterOrder(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Integer> q = cb.createQuery(Integer.class);
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		q.select(cb.max(st.get(SuggestionTemplateEntity_.alternativeOrder)))
				.where(cb.equal(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::orderOrMinusOne);
	}

	private Uni<Integer> maxWcOrder(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Integer> q = cb.createQuery(Integer.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		q.select(cb.max(wc.get(SuggestionTemplateWcEntity_.alternativeOrder)))
				.where(cb.equal(wc.get(SuggestionTemplateWcEntity_.ruleId), ruleId));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::orderOrMinusOne);
	}

	private static int orderOrMinusOne(List<Integer> rows) {
		Integer max = rows.isEmpty() ? null : rows.getFirst();
		return max == null ? -1 : max;
	}

	@Override
	public Uni<Void> discardTemplate(ReactivePersistenceTxContext tx, UUID templateId, long baseVersion) {
		return tx.find(SuggestionTemplateWcEntity.class, templateId).flatMap(existing -> {
			if (existing == null) {
				return Uni.createFrom().voidItem();
			}
			return tx.find(SuggestionTemplateEntity.class, templateId).flatMap(master -> master == null
					? deleteStagedRow(tx, templateId, baseVersion)
					: Uni.createFrom().failure(new EntityNotFoundException(SuggestionTemplateEntity.class, templateId, "No unpublished new Suggestion Template to discard")));
		});
	}

	@Override
	public Uni<Map<UUID, Boolean>> findActiveByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		q.select(cb.tuple(st.get(SuggestionTemplateEntity_.id), st.get(SuggestionTemplateEntity_.active)))
				.where(cb.equal(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId));
		return em.createQuery(q).getResultList()
				.map(rows -> rows.stream().collect(toMap(row -> row.get(0, UUID.class), row -> row.get(1, Boolean.class))));
	}

	@Override
	public Uni<Set<UUID>> findRuleIdsWithStagedTemplates(ReactivePersistenceContext em) {
		return forcm(
				ruleIdsWithStagedFields(em),
				_ -> ruleIdsWithStagedTranslations(em),
				_ -> ruleIdsWithStagedAlternatives(em),
				_ -> ruleIdsWithStagedAlternativeTranslations(em),
				(fromFields, fromTranslations, fromAlternatives, fromAlternativeTranslations) -> {
					fromFields.addAll(fromTranslations);
					fromFields.addAll(fromAlternatives);
					fromFields.addAll(fromAlternativeTranslations);
					return fromFields;
				}
		);
	}

	private Uni<Set<UUID>> ruleIdsWithStagedFields(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		q.select(wc.get(SuggestionTemplateWcEntity_.ruleId)).distinct(true);
		return em.createQuery(q).getResultList().map(HashSet::new);
	}

	private Uni<Set<UUID>> ruleIdsWithStagedTranslations(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		Subquery<UUID> staged = q.subquery(UUID.class);
		Root<SuggestionTemplateTranslationWcEntity> ttwc = staged.from(SuggestionTemplateTranslationWcEntity.class);
		staged.select(ttwc.get(SuggestionTemplateTranslationWcEntity_.suggestionTemplateId));
		q.select(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id)).distinct(true)
				.where(st.get(SuggestionTemplateEntity_.id).in(staged));
		return em.createQuery(q).getResultList().map(HashSet::new);
	}

	private Uni<Set<UUID>> ruleIdsWithStagedAlternatives(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		Subquery<UUID> staged = q.subquery(UUID.class);
		Root<AlternativeIngredientWcEntity> aiwc = staged.from(AlternativeIngredientWcEntity.class);
		staged.select(aiwc.get(AlternativeIngredientWcEntity_.id));
		q.select(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id)).distinct(true)
				.where(st.get(SuggestionTemplateEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id).in(staged));
		return em.createQuery(q).getResultList().map(HashSet::new);
	}

	private Uni<Set<UUID>> ruleIdsWithStagedAlternativeTranslations(ReactivePersistenceContext em) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<UUID> q = cb.createQuery(UUID.class);
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		Subquery<UUID> staged = q.subquery(UUID.class);
		Root<AlternativeIngredientTranslationWcEntity> aitwc = staged.from(AlternativeIngredientTranslationWcEntity.class);
		staged.select(aitwc.get(AlternativeIngredientTranslationWcEntity_.alternativeIngredientId));
		q.select(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id)).distinct(true)
				.where(st.get(SuggestionTemplateEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id).in(staged));
		return em.createQuery(q).getResultList().map(HashSet::new);
	}

	@Override
	public Uni<Map<UUID, UUID>> findAlternativeIdsByRule(ReactivePersistenceContext em, UUID ruleId) {
		return masterAlternativeIdsByRule(em, ruleId).flatMap(master ->
				wcAlternativeIdsByRule(em, ruleId).map(wc -> {
					Map<UUID, UUID> merged = new HashMap<>(master);
					merged.putAll(wc);
					return merged;
				}));
	}

	private Uni<Map<UUID, UUID>> masterAlternativeIdsByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		q.select(cb.tuple(st.get(SuggestionTemplateEntity_.id), st.get(SuggestionTemplateEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id)))
				.where(cb.equal(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toIdMap);
	}

	private Uni<Map<UUID, UUID>> wcAlternativeIdsByRule(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		q.select(cb.tuple(wc.get(SuggestionTemplateWcEntity_.id), wc.get(SuggestionTemplateWcEntity_.alternativeIngredientId)))
				.where(cb.equal(wc.get(SuggestionTemplateWcEntity_.ruleId), ruleId));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toIdMap);
	}

	private static Map<UUID, UUID> toIdMap(List<Tuple> rows) {
		return rows.stream().collect(toMap(row -> row.get(0, UUID.class), row -> row.get(1, UUID.class)));
	}

	@Override
	public Uni<Long> countTemplatesByAlternative(ReactivePersistenceContext em, UUID alternativeIngredientId) {
		return masterCountByAlternative(em, alternativeIngredientId).flatMap(masterCount ->
				wcOnlyCountByAlternative(em, alternativeIngredientId).map(wcOnlyCount -> masterCount + wcOnlyCount));
	}

	private Uni<Long> masterCountByAlternative(ReactivePersistenceContext em, UUID alternativeIngredientId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> q = cb.createQuery(Long.class);
		Root<SuggestionTemplateEntity> st = q.from(SuggestionTemplateEntity.class);
		q.select(cb.count(st)).where(cb.equal(st.get(SuggestionTemplateEntity_.alternativeIngredient).get(AlternativeIngredientEntity_.id), alternativeIngredientId));
		return em.createQuery(q).getResultList().map(rows -> rows.isEmpty() ? 0L : rows.getFirst());
	}

	private Uni<Long> wcOnlyCountByAlternative(ReactivePersistenceContext em, UUID alternativeIngredientId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> q = cb.createQuery(Long.class);
		Root<SuggestionTemplateWcEntity> wc = q.from(SuggestionTemplateWcEntity.class);
		Subquery<UUID> master = q.subquery(UUID.class);
		Root<SuggestionTemplateEntity> masterTemplate = master.from(SuggestionTemplateEntity.class);
		master.select(masterTemplate.get(SuggestionTemplateEntity_.id))
				.where(cb.equal(masterTemplate.get(SuggestionTemplateEntity_.id), wc.get(SuggestionTemplateWcEntity_.id)));
		q.select(cb.count(wc)).where(cb.and(
				cb.equal(wc.get(SuggestionTemplateWcEntity_.alternativeIngredientId), alternativeIngredientId),
				cb.not(cb.exists(master))));
		return em.createQuery(q).getResultList().map(rows -> rows.isEmpty() ? 0L : rows.getFirst());
	}

	@Override
	public Uni<Map<UUID, FieldTranslationLangs>> findFieldTranslationLangsByRule(ReactivePersistenceContext em, UUID ruleId) {
		return masterTranslationValuesByTemplate(em, ruleId).flatMap(master ->
				stagedTranslationValuesByTemplate(em, ruleId).map(staged -> mergeFieldTranslationLangs(master, staged)));
	}

	@Override
	public Uni<Map<RecipeLanguage, VersionedText>> findFieldTranslationsForEdit(ReactivePersistenceContext em, UUID templateId, SuggestionTemplateField field) {
		return masterTranslationsForTemplate(em, templateId).flatMap(master ->
				stagedTranslationsForTemplate(em, templateId).map(staged -> toFieldEditableTranslations(master, staged, field)));
	}

	@Override
	public Uni<Void> stageFieldTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, SuggestionTemplateField field, String value, long baseVersion) {
		return forc(
				tx.find(SuggestionTemplateTranslationWcEntity.class, new SuggestionTemplateTranslationWcEntityId(templateId, lang)),
				_ -> tx.find(SuggestionTemplateTranslationEntity.class, new SuggestionTemplateTranslationEntityId(templateId, lang)),
				(existing, master) -> applyFieldTranslationEdit(tx, templateId, lang, field, value, baseVersion, existing, master)
		);
	}

	@Override
	public Uni<Void> revertFieldTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, SuggestionTemplateField field, long baseVersion) {
		return tx.find(SuggestionTemplateTranslationWcEntity.class, new SuggestionTemplateTranslationWcEntityId(templateId, lang)).flatMap(existing -> {
			if (existing == null) {
				return Uni.createFrom().voidItem();
			}
			return tx.find(SuggestionTemplateTranslationEntity.class, new SuggestionTemplateTranslationEntityId(templateId, lang)).flatMap(master -> {
				String masterValue = masterTranslationField(master, field);
				return matchesMasterAfterSet(existing, field, masterValue, master)
						? deleteStagedTranslation(tx, templateId, lang, baseVersion)
						: bumpFieldTranslation(tx, templateId, lang, translationColumn(field), masterValue, baseVersion);
			});
		});
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

	@Override
	public Uni<Void> setActive(ReactivePersistenceTxContext tx, UUID templateId, boolean active, long baseVersion) {
		return tx.find(SuggestionTemplateWcEntity.class, templateId).flatMap(existing ->
				tx.find(SuggestionTemplateEntity.class, templateId).flatMap(master -> applySetActive(tx, templateId, active, baseVersion, existing, master)));
	}

	private Uni<Void> applySetActive(ReactivePersistenceTxContext tx, UUID templateId, boolean active, long baseVersion, SuggestionTemplateWcEntity existing, SuggestionTemplateEntity master) {
		if (master == null) {
			return Uni.createFrom().failure(new EntityNotFoundException(SuggestionTemplateEntity.class, templateId));
		}
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateEntity.class, templateId));
			}
			return active == master.isActive()
					? Uni.createFrom().voidItem()
					: seedSnapshot(tx, master, wc -> wc.setActive(active)).replaceWithVoid();
		}
		boolean collapses = active == master.isActive()
				&& Objects.equals(existing.getRestriction(), master.getRestriction())
				&& Objects.equals(existing.getEquivalence(), master.getEquivalence())
				&& Objects.equals(existing.getTechniqueNotes(), master.getTechniqueNotes());
		return collapses
				? deleteStagedRow(tx, templateId, baseVersion)
				: bumpStagedField(tx, templateId, SuggestionTemplateWcEntity_.active, active, baseVersion).replaceWithVoid();
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
		wc.setActive(master.isActive());
		wc.setVersion(1L);
		override.accept(wc);
		return tx.persist(wc).replaceWith(wc);
	}

	private <T> Uni<Long> bumpStagedField(ReactivePersistenceTxContext tx, UUID templateId, SingularAttribute<SuggestionTemplateWcEntity, T> field, T value, long baseVersion) {
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
				&& Objects.equals(techniqueNotes, master.getTechniqueNotes())
				&& existing.isActive() == master.isActive();
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
				row -> new StagedSuggestionTemplateOverlay(row.getRestriction(), row.getEquivalence(), row.getTechniqueNotes(), row.isActive(), row.getVersion()),
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

	private Uni<Void> applyFieldTranslationEdit(
			ReactivePersistenceTxContext tx,
			UUID templateId,
			RecipeLanguage lang,
			SuggestionTemplateField field,
			String value,
			long baseVersion,
			SuggestionTemplateTranslationWcEntity existing,
			SuggestionTemplateTranslationEntity master
	) {
		boolean matchesMaster = matchesMasterAfterSet(existing, field, value, master);
		if (existing == null) {
			if (baseVersion != 0L) {
				return Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateTranslationEntity.class, templateId));
			}
			return matchesMaster ? Uni.createFrom().voidItem() : seedFieldTranslation(tx, templateId, lang, master, field, value);
		}
		return matchesMaster
				? deleteStagedTranslation(tx, templateId, lang, baseVersion)
				: bumpFieldTranslation(tx, templateId, lang, translationColumn(field), value, baseVersion);
	}

	private Uni<Void> seedFieldTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, SuggestionTemplateTranslationEntity master, SuggestionTemplateField field, String value) {
		var wc = new SuggestionTemplateTranslationWcEntity();
		wc.setSuggestionTemplateId(templateId);
		wc.setLang(lang);
		wc.setRestriction(masterTranslationField(master, SuggestionTemplateField.RESTRICTION));
		wc.setEquivalence(masterTranslationField(master, SuggestionTemplateField.EQUIVALENCE));
		wc.setTechniqueNotes(masterTranslationField(master, SuggestionTemplateField.TECHNIQUE_NOTES));
		setTranslationField(wc, field, value);
		wc.setVersion(1L);
		return tx.persist(wc).replaceWithVoid();
	}

	private Uni<Void> bumpFieldTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, SingularAttribute<SuggestionTemplateTranslationWcEntity, String> field, String value, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaUpdate<SuggestionTemplateTranslationWcEntity> cu = cb.createCriteriaUpdate(SuggestionTemplateTranslationWcEntity.class);
		Root<SuggestionTemplateTranslationWcEntity> wc = cu.getRoot();
		cu.set(wc.get(field), value);
		cu.set(wc.get(SuggestionTemplateTranslationWcEntity_.version), cb.sum(wc.get(SuggestionTemplateTranslationWcEntity_.version), 1L));
		cu.where(translationRowAt(cb, wc, templateId, lang, baseVersion));
		return tx.createUpdate(cu).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateTranslationEntity.class, templateId)));
	}

	private Uni<Void> deleteStagedTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, long baseVersion) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<SuggestionTemplateTranslationWcEntity> cd = cb.createCriteriaDelete(SuggestionTemplateTranslationWcEntity.class);
		Root<SuggestionTemplateTranslationWcEntity> wc = cd.getRoot();
		cd.where(translationRowAt(cb, wc, templateId, lang, baseVersion));
		return tx.createDelete(cd).execute().flatMap(rowsAffected -> rowsAffected == 1
				? Uni.createFrom().voidItem()
				: Uni.createFrom().failure(new StaleVersionException(SuggestionTemplateTranslationEntity.class, templateId)));
	}

	private static Predicate translationRowAt(CriteriaBuilder cb, Root<SuggestionTemplateTranslationWcEntity> wc, UUID templateId, RecipeLanguage lang, long baseVersion) {
		return cb.and(
				cb.equal(wc.get(SuggestionTemplateTranslationWcEntity_.suggestionTemplateId), templateId),
				cb.equal(wc.get(SuggestionTemplateTranslationWcEntity_.lang), lang),
				cb.equal(wc.get(SuggestionTemplateTranslationWcEntity_.version), baseVersion));
	}

	private static boolean matchesMasterAfterSet(SuggestionTemplateTranslationWcEntity existing, SuggestionTemplateField field, String value, SuggestionTemplateTranslationEntity master) {
		for (SuggestionTemplateField f : SuggestionTemplateField.values()) {
			String effective = f == field ? value
					: existing != null ? wcField(existing, f)
					  : masterTranslationField(master, f);
			if (!Objects.equals(effective, masterTranslationField(master, f))) {
				return false;
			}
		}
		return true;
	}

	private Uni<Map<UUID, Map<RecipeLanguage, TranslationValues>>> masterTranslationValuesByTemplate(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<SuggestionTemplateTranslationEntity> tt = q.from(SuggestionTemplateTranslationEntity.class);
		q.select(cb.tuple(
				tt.get(SuggestionTemplateTranslationEntity_.suggestionTemplate).get(SuggestionTemplateEntity_.id),
				tt.get(SuggestionTemplateTranslationEntity_.lang),
				tt.get(SuggestionTemplateTranslationEntity_.restriction),
				tt.get(SuggestionTemplateTranslationEntity_.equivalence),
				tt.get(SuggestionTemplateTranslationEntity_.techniqueNotes)
		)).where(cb.equal(tt.get(SuggestionTemplateTranslationEntity_.suggestionTemplate).get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toValuesByTemplateThenLang);
	}

	private Uni<Map<UUID, Map<RecipeLanguage, TranslationValues>>> stagedTranslationValuesByTemplate(ReactivePersistenceContext em, UUID ruleId) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> q = cb.createTupleQuery();
		Root<SuggestionTemplateTranslationWcEntity> ttwc = q.from(SuggestionTemplateTranslationWcEntity.class);
		Subquery<UUID> ruleTemplates = q.subquery(UUID.class);
		Root<SuggestionTemplateEntity> st = ruleTemplates.from(SuggestionTemplateEntity.class);
		ruleTemplates.select(st.get(SuggestionTemplateEntity_.id)).where(cb.equal(st.get(SuggestionTemplateEntity_.rule).get(RuleEntity_.id), ruleId));
		q.select(cb.tuple(
				ttwc.get(SuggestionTemplateTranslationWcEntity_.suggestionTemplateId),
				ttwc.get(SuggestionTemplateTranslationWcEntity_.lang),
				ttwc.get(SuggestionTemplateTranslationWcEntity_.restriction),
				ttwc.get(SuggestionTemplateTranslationWcEntity_.equivalence),
				ttwc.get(SuggestionTemplateTranslationWcEntity_.techniqueNotes)
		)).where(ttwc.get(SuggestionTemplateTranslationWcEntity_.suggestionTemplateId).in(ruleTemplates));
		return em.createQuery(q).getResultList().map(SuggestionTemplateDaoImpl::toValuesByTemplateThenLang);
	}

	private static Map<UUID, Map<RecipeLanguage, TranslationValues>> toValuesByTemplateThenLang(List<Tuple> rows) {
		Map<UUID, Map<RecipeLanguage, TranslationValues>> result = new HashMap<>();
		for (Tuple row : rows) {
			result.computeIfAbsent(row.get(0, UUID.class), _ -> new EnumMap<>(RecipeLanguage.class))
					.put(row.get(1, RecipeLanguage.class), new TranslationValues(row.get(2, String.class), row.get(3, String.class), row.get(4, String.class)));
		}
		return result;
	}

	private static Map<UUID, FieldTranslationLangs> mergeFieldTranslationLangs(
			Map<UUID, Map<RecipeLanguage, TranslationValues>> master,
			Map<UUID, Map<RecipeLanguage, TranslationValues>> staged
	) {
		Set<UUID> templateIds = new HashSet<>(master.keySet());
		templateIds.addAll(staged.keySet());
		Map<UUID, FieldTranslationLangs> result = new HashMap<>();
		for (UUID templateId : templateIds) {
			Map<RecipeLanguage, TranslationValues> m = master.getOrDefault(templateId, Map.of());
			Map<RecipeLanguage, TranslationValues> s = staged.getOrDefault(templateId, Map.of());
			result.put(templateId, new FieldTranslationLangs(
					fieldLangs(m, s, SuggestionTemplateField.RESTRICTION),
					fieldLangs(m, s, SuggestionTemplateField.EQUIVALENCE),
					fieldLangs(m, s, SuggestionTemplateField.TECHNIQUE_NOTES)));
		}
		return result;
	}

	private static TranslationLangs fieldLangs(Map<RecipeLanguage, TranslationValues> master, Map<RecipeLanguage, TranslationValues> staged, SuggestionTemplateField field) {
		Set<RecipeLanguage> present = EnumSet.noneOf(RecipeLanguage.class);
		Set<RecipeLanguage> stagedLangs = EnumSet.noneOf(RecipeLanguage.class);
		master.forEach((lang, values) -> {
			if (valueOf(values, field) != null) {
				present.add(lang);
			}
		});
		staged.forEach((lang, values) -> {
			String masterValue = master.containsKey(lang) ? valueOf(master.get(lang), field) : null;
			if (!Objects.equals(valueOf(values, field), masterValue)) {
				stagedLangs.add(lang);
			}
		});
		return new TranslationLangs(present, stagedLangs);
	}

	private Uni<Map<RecipeLanguage, SuggestionTemplateTranslationEntity>> masterTranslationsForTemplate(ReactivePersistenceContext em, UUID templateId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateTranslationEntity.class);
		Root<SuggestionTemplateTranslationEntity> tt = q.from(SuggestionTemplateTranslationEntity.class);
		q.select(tt).where(cb.equal(tt.get(SuggestionTemplateTranslationEntity_.suggestionTemplate).get(SuggestionTemplateEntity_.id), templateId));
		return em.createQuery(q).getResultList().map(rows -> rows.stream().collect(toMap(SuggestionTemplateTranslationEntity::getLang, t -> t)));
	}

	private Uni<Map<RecipeLanguage, SuggestionTemplateTranslationWcEntity>> stagedTranslationsForTemplate(ReactivePersistenceContext em, UUID templateId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(SuggestionTemplateTranslationWcEntity.class);
		Root<SuggestionTemplateTranslationWcEntity> ttwc = q.from(SuggestionTemplateTranslationWcEntity.class);
		q.select(ttwc).where(cb.equal(ttwc.get(SuggestionTemplateTranslationWcEntity_.suggestionTemplateId), templateId));
		return em.createQuery(q).getResultList().map(rows -> rows.stream().collect(toMap(SuggestionTemplateTranslationWcEntity::getLang, w -> w)));
	}

	private static Map<RecipeLanguage, VersionedText> toFieldEditableTranslations(
			Map<RecipeLanguage, SuggestionTemplateTranslationEntity> master,
			Map<RecipeLanguage, SuggestionTemplateTranslationWcEntity> staged,
			SuggestionTemplateField field
	) {
		Map<RecipeLanguage, VersionedText> result = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			SuggestionTemplateTranslationWcEntity wc = staged.get(lang);
			result.put(lang, wc != null
					? new VersionedText(wcField(wc, field), wc.getVersion())
					: new VersionedText(masterTranslationField(master.get(lang), field), 0L));
		}
		return result;
	}

	private static SingularAttribute<SuggestionTemplateTranslationWcEntity, String> translationColumn(SuggestionTemplateField field) {
		return switch (field) {
			case RESTRICTION -> SuggestionTemplateTranslationWcEntity_.restriction;
			case EQUIVALENCE -> SuggestionTemplateTranslationWcEntity_.equivalence;
			case TECHNIQUE_NOTES -> SuggestionTemplateTranslationWcEntity_.techniqueNotes;
		};
	}

	private static String masterTranslationField(SuggestionTemplateTranslationEntity master, SuggestionTemplateField field) {
		if (master == null) {
			return null;
		}
		return switch (field) {
			case RESTRICTION -> master.getRestriction();
			case EQUIVALENCE -> master.getEquivalence();
			case TECHNIQUE_NOTES -> master.getTechniqueNotes();
		};
	}

	private static String wcField(SuggestionTemplateTranslationWcEntity wc, SuggestionTemplateField field) {
		return switch (field) {
			case RESTRICTION -> wc.getRestriction();
			case EQUIVALENCE -> wc.getEquivalence();
			case TECHNIQUE_NOTES -> wc.getTechniqueNotes();
		};
	}

	private static void setTranslationField(SuggestionTemplateTranslationWcEntity wc, SuggestionTemplateField field, String value) {
		switch (field) {
			case RESTRICTION -> wc.setRestriction(value);
			case EQUIVALENCE -> wc.setEquivalence(value);
			case TECHNIQUE_NOTES -> wc.setTechniqueNotes(value);
		}
	}

	private static String valueOf(TranslationValues values, SuggestionTemplateField field) {
		return switch (field) {
			case RESTRICTION -> values.restriction();
			case EQUIVALENCE -> values.equivalence();
			case TECHNIQUE_NOTES -> values.techniqueNotes();
		};
	}

	private record TranslationValues(String restriction, String equivalence, String techniqueNotes) {
	}
}
