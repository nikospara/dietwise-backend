package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.suggestions.FieldTranslationLangs;
import eu.dietwise.services.model.suggestions.NewSuggestionTemplate;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.services.v1.BackofficeSuggestionTemplatesService;
import eu.dietwise.services.v1.types.AddedTemplate;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeSuggestionTemplatesServiceImpl implements BackofficeSuggestionTemplatesService {
	private final SuggestionTemplateDao suggestionTemplateDao;
	private final AlternativeIngredientDao alternativeIngredientDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeSuggestionTemplatesServiceImpl(
			SuggestionTemplateDao suggestionTemplateDao,
			AlternativeIngredientDao alternativeIngredientDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.suggestionTemplateDao = suggestionTemplateDao;
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<List<StagedSuggestionTemplate>> listSuggestionTemplates(User user, RuleId ruleId) {
		authorization.requireAdmin(user);
		UUID id = ruleId.asUuid();
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				suggestionTemplateDao.findByRule(em, id),
				_ -> suggestionTemplateDao.findStagedOverlayByRule(em, id),
				(_, _) -> suggestionTemplateDao.findFieldTranslationLangsByRule(em, id),
				(_, _, _) -> suggestionTemplateDao.findActiveByRule(em, id),
				(_, _, _, _) -> suggestionTemplateDao.findNewByRule(em, id),
				(_, _, _, _, _) -> alternativeOverlay(em, id),
				BackofficeSuggestionTemplatesServiceImpl::mergeTemplates
		));
	}

	private Uni<AlternativeOverlay> alternativeOverlay(ReactivePersistenceContext em, UUID ruleId) {
		return forcm(
				suggestionTemplateDao.findAlternativeIdsByRule(em, ruleId),
				_ -> alternativeIngredientDao.findStagedNames(em),
				(_, _) -> alternativeIngredientDao.findTranslationLangs(em),
				AlternativeOverlay::new
		);
	}

	@Override
	public Uni<List<ReferenceOption>> alternativeIngredientOptions(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(alternativeIngredientDao::listOptions);
	}

	@Override
	public Uni<AddedTemplate> addSuggestionTemplate(User user, RuleId ruleId, UUID alternativeIngredientId) {
		authorization.requireAdmin(user);
		UUID id = ruleId.asUuid();
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.findTemplateIdByRuleAndAlternative(tx, id, alternativeIngredientId)
				.flatMap(existing -> existing
						.map(templateId -> Uni.createFrom().item(new AddedTemplate(templateId, false)))
						.orElseGet(() -> suggestionTemplateDao.addTemplate(tx, id, alternativeIngredientId).map(newId -> new AddedTemplate(newId, true)))));
	}

	@Override
	public Uni<Void> discardSuggestionTemplate(User user, SuggestionTemplateId templateId, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.discardTemplate(tx, templateId.asUuid(), baseVersion));
	}

	@Override
	public Uni<Long> stageSuggestionTemplateField(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, String value, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.stageField(tx, templateId.asUuid(), field, value, baseVersion));
	}

	@Override
	public Uni<Void> revertSuggestionTemplateField(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.revertField(tx, templateId.asUuid(), field, baseVersion));
	}

	@Override
	public Uni<Void> setSuggestionTemplateActive(User user, SuggestionTemplateId templateId, boolean active, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.setActive(tx, templateId.asUuid(), active, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, VersionedText>> templateFieldTranslationsForEdit(User user, SuggestionTemplateId templateId, SuggestionTemplateField field) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> suggestionTemplateDao.findFieldTranslationsForEdit(em, templateId.asUuid(), field));
	}

	@Override
	public Uni<Void> stageTemplateFieldTranslation(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, RecipeLanguage lang, String value, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.stageFieldTranslation(tx, templateId.asUuid(), lang, field, value, baseVersion));
	}

	@Override
	public Uni<Void> revertTemplateFieldTranslation(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.revertFieldTranslation(tx, templateId.asUuid(), lang, field, baseVersion));
	}

	private static List<StagedSuggestionTemplate> mergeTemplates(
			List<SuggestionTemplate> master,
			Map<UUID, StagedSuggestionTemplateOverlay> overlays,
			Map<UUID, FieldTranslationLangs> translationLangs,
			Map<UUID, Boolean> masterActive,
			List<NewSuggestionTemplate> newTemplates,
			AlternativeOverlay alternativeOverlay
	) {
		Stream<StagedSuggestionTemplate> published = master.stream()
				.map(template -> toStagedSuggestionTemplate(
						template,
						overlays.get(template.getId().asUuid()),
						translationLangs.get(template.getId().asUuid()),
						masterActive.getOrDefault(template.getId().asUuid(), true),
						alternativeOverlay));
		Stream<StagedSuggestionTemplate> added = newTemplates.stream()
				.map(newTemplate -> toNewStagedSuggestionTemplate(newTemplate, alternativeOverlay));
		return Stream.concat(published, added).toList();
	}

	private static StagedSuggestionTemplate toStagedSuggestionTemplate(SuggestionTemplate master, StagedSuggestionTemplateOverlay overlay, FieldTranslationLangs translationLangs, boolean masterActive, AlternativeOverlay alternativeOverlay) {
		UUID alternativeId = alternativeOverlay.alternativeIdsByTemplate().get(master.getId().asUuid());
		Map<SuggestionTemplateField, Map<RecipeLanguage, TranslationState>> translations = templateTranslationStates(translationLangs);
		Map<RecipeLanguage, TranslationState> alternativeTranslations = alternativeTranslationStates(alternativeId, alternativeOverlay.translationLangs());
		String effectiveName = effectiveAlternativeName(master, alternativeId, alternativeOverlay.stagedNames());
		if (overlay == null) {
			SuggestionTemplate effective = ImmutableSuggestionTemplate.builder().from(master)
					.alternative(new AlternativeIngredientImpl(effectiveName))
					.build();
			return new StagedSuggestionTemplate(effective, alternativeId, EnumSet.noneOf(SuggestionTemplateField.class), translations, alternativeTranslations, masterActive, false, true, 0L);
		}
		SuggestionTemplate effective = ImmutableSuggestionTemplate.builder().from(master)
				.alternative(new AlternativeIngredientImpl(effectiveName))
				.restriction(Optional.ofNullable(overlay.restriction()))
				.equivalence(Optional.ofNullable(overlay.equivalence()))
				.techniqueNotes(Optional.ofNullable(overlay.techniqueNotes()))
				.build();
		Set<SuggestionTemplateField> changedFields = EnumSet.noneOf(SuggestionTemplateField.class);
		if (!Objects.equals(master.getRestriction().orElse(null), overlay.restriction())) {
			changedFields.add(SuggestionTemplateField.RESTRICTION);
		}
		if (!Objects.equals(master.getEquivalence().orElse(null), overlay.equivalence())) {
			changedFields.add(SuggestionTemplateField.EQUIVALENCE);
		}
		if (!Objects.equals(master.getTechniqueNotes().orElse(null), overlay.techniqueNotes())) {
			changedFields.add(SuggestionTemplateField.TECHNIQUE_NOTES);
		}
		return new StagedSuggestionTemplate(effective, alternativeId, changedFields, translations, alternativeTranslations, overlay.active(), overlay.active() != masterActive, true, overlay.version());
	}

	private static StagedSuggestionTemplate toNewStagedSuggestionTemplate(NewSuggestionTemplate newTemplate, AlternativeOverlay alternativeOverlay) {
		SuggestionTemplate master = newTemplate.template();
		UUID alternativeId = alternativeOverlay.alternativeIdsByTemplate().get(master.getId().asUuid());
		String effectiveName = effectiveAlternativeName(master, alternativeId, alternativeOverlay.stagedNames());
		SuggestionTemplate effective = ImmutableSuggestionTemplate.builder().from(master)
				.alternative(new AlternativeIngredientImpl(effectiveName))
				.build();
		return new StagedSuggestionTemplate(
				effective,
				alternativeId,
				EnumSet.noneOf(SuggestionTemplateField.class),
				templateTranslationStates(null),
				alternativeTranslationStates(alternativeId, alternativeOverlay.translationLangs()),
				true,
				false,
				false,
				newTemplate.version());
	}

	private static String effectiveAlternativeName(SuggestionTemplate master, UUID alternativeId, Map<UUID, String> stagedNames) {
		if (alternativeId == null) {
			return master.getAlternative().asString();
		}
		return stagedNames.getOrDefault(alternativeId, master.getAlternative().asString());
	}

	private static Map<RecipeLanguage, TranslationState> alternativeTranslationStates(UUID alternativeId, Map<UUID, TranslationLangs> langsById) {
		return BackofficeTranslations.translationStates(alternativeId == null ? null : langsById.get(alternativeId));
	}

	/**
	 * Per-template AlternativeIngredient context for one Rule's panel: the id of the AlternativeIngredient each template
	 * suggests, the shared staged English names (so a renamed entity shows on every referencing template), and the shared
	 * AlternativeIngredients' translation completeness, each keyed by AlternativeIngredient id.
	 */
	private record AlternativeOverlay(
			Map<UUID, UUID> alternativeIdsByTemplate,
			Map<UUID, String> stagedNames,
			Map<UUID, TranslationLangs> translationLangs
	) {
	}

	private static Map<SuggestionTemplateField, Map<RecipeLanguage, TranslationState>> templateTranslationStates(FieldTranslationLangs langs) {
		Map<SuggestionTemplateField, Map<RecipeLanguage, TranslationState>> states = new EnumMap<>(SuggestionTemplateField.class);
		states.put(SuggestionTemplateField.RESTRICTION, BackofficeTranslations.translationStates(langs == null ? null : langs.restriction()));
		states.put(SuggestionTemplateField.EQUIVALENCE, BackofficeTranslations.translationStates(langs == null ? null : langs.equivalence()));
		states.put(SuggestionTemplateField.TECHNIQUE_NOTES, BackofficeTranslations.translationStates(langs == null ? null : langs.techniqueNotes()));
		return states;
	}
}
