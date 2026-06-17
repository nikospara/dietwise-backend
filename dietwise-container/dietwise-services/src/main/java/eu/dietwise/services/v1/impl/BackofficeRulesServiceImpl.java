package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Arrays;
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

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.suggestions.FieldTranslationLangs;
import eu.dietwise.services.model.suggestions.NewSuggestionTemplate;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.services.model.suggestions.RuleReferences;
import eu.dietwise.services.model.suggestions.StagedNewRule;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.types.AddedTemplate;
import eu.dietwise.services.v1.types.AlternativeIngredientForEdit;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.RuleChangeState;
import eu.dietwise.services.v1.types.RuleField;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeRulesServiceImpl implements BackofficeRulesService {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

	private final RuleDao ruleDao;
	private final RecommendationDao recommendationDao;
	private final TriggerIngredientDao triggerIngredientDao;
	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final SuggestionTemplateDao suggestionTemplateDao;
	private final AlternativeIngredientDao alternativeIngredientDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeRulesServiceImpl(
			RuleDao ruleDao,
			RecommendationDao recommendationDao,
			TriggerIngredientDao triggerIngredientDao,
			RoleOrTechniqueDao roleOrTechniqueDao,
			SuggestionTemplateDao suggestionTemplateDao,
			AlternativeIngredientDao alternativeIngredientDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.ruleDao = ruleDao;
		this.recommendationDao = recommendationDao;
		this.triggerIngredientDao = triggerIngredientDao;
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.suggestionTemplateDao = suggestionTemplateDao;
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<List<StagedRule>> listRules(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				ruleDao.findAll(em, RecipeLanguage.EN),
				_ -> ruleDao.findStagedOverlay(em),
				(_, _) -> ruleDao.findNewRules(em, RecipeLanguage.EN),
				(_, _, _) -> ruleDao.findReferenceIds(em),
				(_, _, _, _) -> triggerIngredientDao.findStagedNames(em),
				(_, _, _, _, _) -> roleOrTechniqueDao.findStagedNames(em),
				(_, _, _, _, _, _) -> translationCompleteness(em),
				(_, _, _, _, _, _, _) -> suggestionTemplateDao.findRuleIdsWithStagedTemplates(em),
				BackofficeRulesServiceImpl::merge
		));
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
				BackofficeRulesServiceImpl::mergeTemplates
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
	public Uni<ReferenceOption> createAlternativeIngredient(User user, String name) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.listOptions(tx).flatMap(options -> nameExists(options, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("An Alternative Ingredient named '" + name + "' already exists"))
				: alternativeIngredientDao.createAlternativeIngredient(tx, name).map(id -> new ReferenceOption(id, name))));
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
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.stageFieldTranslation(tx, templateId.asUuid(), lang, field, value, baseVersion));
	}

	@Override
	public Uni<Void> revertTemplateFieldTranslation(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> suggestionTemplateDao.revertFieldTranslation(tx, templateId.asUuid(), lang, field, baseVersion));
	}

	private Uni<TranslationCompleteness> translationCompleteness(ReactivePersistenceContext em) {
		return forcm(
				ruleDao.findRationaleTranslationLangs(em),
				_ -> triggerIngredientDao.findTranslationLangs(em),
				(_, _) -> roleOrTechniqueDao.findTranslationLangs(em),
				TranslationCompleteness::new
		);
	}

	@Override
	public Uni<Long> stageRationale(User user, RuleId ruleId, String rationale, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.stageRationale(tx, ruleId.asUuid(), rationale, baseVersion));
	}

	@Override
	public Uni<Void> revertRationale(User user, RuleId ruleId, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.revertRationale(tx, ruleId.asUuid(), baseVersion));
	}

	@Override
	public Uni<Void> setActive(User user, RuleId ruleId, boolean active, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.setActive(tx, ruleId.asUuid(), active, baseVersion));
	}

	@Override
	public Uni<RuleId> createRule(User user, UUID recommendationId, UUID triggerIngredientId, UUID roleOrTechniqueId) {
		authorization.requireAdmin(user);
		var businessKey = new RuleBusinessKey(recommendationId, triggerIngredientId, roleOrTechniqueId);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.findBusinessKeys(tx).flatMap(existing -> existing.contains(businessKey)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Rule with this recommendation, trigger ingredient and role or technique already exists"))
				: ruleDao.createRule(tx, recommendationId, triggerIngredientId, roleOrTechniqueId).map(id -> new GenericRuleId(id.toString()))));
	}

	@Override
	public Uni<Void> discardNewRule(User user, RuleId ruleId, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.discardNewRule(tx, ruleId.asUuid(), baseVersion));
	}

	@Override
	public Uni<NewRuleOptions> newRuleOptions(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				recommendationDao.listOptions(em),
				_ -> triggerIngredientDao.listOptions(em),
				_ -> roleOrTechniqueDao.listOptions(em),
				NewRuleOptions::new
		));
	}

	@Override
	public Uni<ReferenceOption> createTriggerIngredient(User user, String name) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.listOptions(tx).flatMap(options -> nameExists(options, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Trigger Ingredient named '" + name + "' already exists"))
				: triggerIngredientDao.createTriggerIngredient(tx, name).map(id -> new ReferenceOption(id, name))));
	}

	@Override
	public Uni<ReferenceOption> createRoleOrTechnique(User user, String name) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.listOptions(tx).flatMap(options -> nameExists(options, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Role or Technique named '" + name + "' already exists"))
				: roleOrTechniqueDao.createRoleOrTechnique(tx, name).map(id -> new ReferenceOption(id, name))));
	}

	@Override
	public Uni<ReferenceDetails> triggerIngredientForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> triggerIngredientDao.findEditableById(em, id));
	}

	@Override
	public Uni<ReferenceDetails> roleOrTechniqueForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> roleOrTechniqueDao.findEditableById(em, id));
	}

	@Override
	public Uni<Void> editTriggerIngredient(User user, UUID id, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.listOptions(tx).flatMap(options -> nameTaken(options, id, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Trigger Ingredient named '" + name + "' already exists"))
				: triggerIngredientDao.editTriggerIngredient(tx, id, name, explanationForLlm, baseVersion)));
	}

	@Override
	public Uni<Void> editRoleOrTechnique(User user, UUID id, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.listOptions(tx).flatMap(options -> nameTaken(options, id, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Role or Technique named '" + name + "' already exists"))
				: roleOrTechniqueDao.editRoleOrTechnique(tx, id, name, explanationForLlm, baseVersion)));
	}

	@Override
	public Uni<Void> revertTriggerIngredient(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.revertTriggerIngredient(tx, id, baseVersion));
	}

	@Override
	public Uni<Void> revertRoleOrTechnique(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.revertRoleOrTechnique(tx, id, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, VersionedText>> rationaleTranslationsForEdit(User user, RuleId ruleId) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> ruleDao.findRationaleTranslationsForEdit(em, ruleId.asUuid()));
	}

	@Override
	public Uni<Void> stageRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, String rationale, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.stageRationaleTranslation(tx, ruleId.asUuid(), lang, rationale, baseVersion));
	}

	@Override
	public Uni<Void> revertRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.revertRationaleTranslation(tx, ruleId.asUuid(), lang, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> triggerIngredientTranslationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> triggerIngredientDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageTriggerIngredientTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.stageTranslation(tx, id, lang, name, explanationForLlm, baseVersion));
	}

	@Override
	public Uni<Void> revertTriggerIngredientTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.revertTranslation(tx, id, lang, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> roleOrTechniqueTranslationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> roleOrTechniqueDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageRoleOrTechniqueTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.stageTranslation(tx, id, lang, name, explanationForLlm, baseVersion));
	}

	@Override
	public Uni<Void> revertRoleOrTechniqueTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.revertTranslation(tx, id, lang, baseVersion));
	}

	@Override
	public Uni<AlternativeIngredientForEdit> alternativeIngredientForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				alternativeIngredientDao.findEditableById(em, id),
				_ -> suggestionTemplateDao.countTemplatesByAlternative(em, id),
				(details, count) -> new AlternativeIngredientForEdit(details.name(), details.explanationForLlm(), details.version(), details.published(), count)
		));
	}

	@Override
	public Uni<Void> editAlternativeIngredient(User user, UUID id, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.listOptions(tx).flatMap(options -> nameTaken(options, id, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("An Alternative Ingredient named '" + name + "' already exists"))
				: alternativeIngredientDao.editAlternativeIngredient(tx, id, name, explanationForLlm, baseVersion)));
	}

	@Override
	public Uni<Void> revertAlternativeIngredient(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.revertAlternativeIngredient(tx, id, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> alternativeIngredientTranslationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> alternativeIngredientDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageAlternativeIngredientTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.stageTranslation(tx, id, lang, name, explanationForLlm, baseVersion));
	}

	@Override
	public Uni<Void> revertAlternativeIngredientTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.revertTranslation(tx, id, lang, baseVersion));
	}

	private static void requireTranslatableLanguage(RecipeLanguage lang) {
		if (lang == RecipeLanguage.EN) {
			throw new IllegalArgumentException("English is the master value, not a translation");
		}
	}

	private static boolean nameExists(List<ReferenceOption> options, String name) {
		return options.stream().anyMatch(option -> option.name().equalsIgnoreCase(name));
	}

	private static boolean nameTaken(List<ReferenceOption> options, UUID id, String name) {
		return options.stream().anyMatch(option -> !option.id().equals(id) && option.name().equalsIgnoreCase(name));
	}

	private static List<StagedRule> merge(
			List<Rule> master,
			Map<UUID, StagedRuleOverlay> overlays,
			List<StagedNewRule> newRules,
			Map<UUID, RuleReferences> referenceIds,
			Map<UUID, String> triggerStagedNames,
			Map<UUID, String> roleStagedNames,
			TranslationCompleteness completeness,
			Set<UUID> rulesWithStagedTemplates
	) {
		Stream<StagedRule> published = master.stream().map(rule ->
				toPublishedStagedRule(rule, overlays.get(rule.getId().asUuid()), referenceIds.get(rule.getId().asUuid()), triggerStagedNames, roleStagedNames, completeness, rulesWithStagedTemplates.contains(rule.getId().asUuid())));
		Stream<StagedRule> created = newRules.stream().map(newRule ->
				toNewStagedRule(newRule, referenceIds.get(newRule.rule().getId().asUuid()), triggerStagedNames, roleStagedNames, completeness, rulesWithStagedTemplates.contains(newRule.rule().getId().asUuid())));
		return Stream.concat(published, created).toList();
	}

	private static StagedRule toPublishedStagedRule(
			Rule master,
			StagedRuleOverlay overlay,
			RuleReferences references,
			Map<UUID, String> triggerStagedNames,
			Map<UUID, String> roleStagedNames,
			TranslationCompleteness completeness,
			boolean hasStagedTemplates
	) {
		UUID triggerId = references.triggerIngredientId();
		UUID roleId = references.roleOrTechniqueId();
		String rationale = overlay == null ? master.getRationale() : overlay.rationale();
		boolean active = overlay == null ? master.isActive() : overlay.active();
		long version = overlay == null ? 0L : overlay.version();
		Rule effective = ImmutableRule.builder().from(master)
				.triggerIngredient(new TriggerIngredientImpl(effectiveTriggerName(master, triggerId, triggerStagedNames)))
				.roleOrTechnique(effectiveRole(master, roleId, roleStagedNames))
				.rationale(rationale)
				.isActive(active)
				.build();
		Set<RuleField> changedFields = EnumSet.noneOf(RuleField.class);
		if (overlay != null && !Objects.equals(master.getRationale(), overlay.rationale())) {
			changedFields.add(RuleField.RATIONALE);
		}
		if (overlay != null && master.isActive() != overlay.active()) {
			changedFields.add(RuleField.ACTIVE);
		}
		addSharedChanges(changedFields, triggerId, roleId, triggerStagedNames, roleStagedNames);
		if (hasStagedTemplates) {
			changedFields.add(RuleField.SUGGESTION_TEMPLATES);
		}
		RuleChangeState changeState = changedFields.contains(RuleField.RATIONALE) || changedFields.contains(RuleField.ACTIVE)
				? RuleChangeState.CHANGED
				: RuleChangeState.UNCHANGED;
		return new StagedRule(
				effective, triggerId, roleId, changeState, changedFields,
				translationStates(master.getId().asUuid(), completeness.rationale()),
				translationStates(triggerId, completeness.triggerIngredient()),
				roleId == null ? Map.of() : translationStates(roleId, completeness.roleOrTechnique()),
				version);
	}

	private static StagedRule toNewStagedRule(
			StagedNewRule newRule,
			RuleReferences references,
			Map<UUID, String> triggerStagedNames,
			Map<UUID, String> roleStagedNames,
			TranslationCompleteness completeness,
			boolean hasStagedTemplates
	) {
		UUID triggerId = references.triggerIngredientId();
		UUID roleId = references.roleOrTechniqueId();
		Set<RuleField> changedFields = EnumSet.noneOf(RuleField.class);
		addSharedChanges(changedFields, triggerId, roleId, triggerStagedNames, roleStagedNames);
		if (hasStagedTemplates) {
			changedFields.add(RuleField.SUGGESTION_TEMPLATES);
		}
		return new StagedRule(
				newRule.rule(), triggerId, roleId, RuleChangeState.NEW, changedFields,
				translationStates(newRule.rule().getId().asUuid(), completeness.rationale()),
				translationStates(triggerId, completeness.triggerIngredient()),
				roleId == null ? Map.of() : translationStates(roleId, completeness.roleOrTechnique()),
				newRule.version());
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
		return translationStates(alternativeId == null ? null : langsById.get(alternativeId));
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
		states.put(SuggestionTemplateField.RESTRICTION, translationStates(langs == null ? null : langs.restriction()));
		states.put(SuggestionTemplateField.EQUIVALENCE, translationStates(langs == null ? null : langs.equivalence()));
		states.put(SuggestionTemplateField.TECHNIQUE_NOTES, translationStates(langs == null ? null : langs.techniqueNotes()));
		return states;
	}

	private static Map<RecipeLanguage, TranslationState> translationStates(UUID id, Map<UUID, TranslationLangs> byId) {
		return translationStates(byId.get(id));
	}

	private static Map<RecipeLanguage, TranslationState> translationStates(TranslationLangs langs) {
		Set<RecipeLanguage> present = langs == null ? Set.of() : langs.present();
		Set<RecipeLanguage> staged = langs == null ? Set.of() : langs.staged();
		Map<RecipeLanguage, TranslationState> states = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			states.put(lang, translationState(lang, present, staged));
		}
		return states;
	}

	private static TranslationState translationState(RecipeLanguage lang, Set<RecipeLanguage> present, Set<RecipeLanguage> staged) {
		if (staged.contains(lang)) {
			return TranslationState.STAGED;
		}
		return present.contains(lang) ? TranslationState.PRESENT : TranslationState.MISSING;
	}

	/**
	 * The per-language translation completeness of every translatable thing the grid shows: a Rule's rationale keyed by
	 * Rule id, and the shared Trigger Ingredients and Roles or Techniques each keyed by their own id.
	 */
	private record TranslationCompleteness(
			Map<UUID, TranslationLangs> rationale,
			Map<UUID, TranslationLangs> triggerIngredient,
			Map<UUID, TranslationLangs> roleOrTechnique
	) {
	}

	private static void addSharedChanges(Set<RuleField> changedFields, UUID triggerId, UUID roleId, Map<UUID, String> triggerStagedNames, Map<UUID, String> roleStagedNames) {
		if (triggerStagedNames.containsKey(triggerId)) {
			changedFields.add(RuleField.TRIGGER_INGREDIENT);
		}
		if (roleId != null && roleStagedNames.containsKey(roleId)) {
			changedFields.add(RuleField.ROLE_OR_TECHNIQUE);
		}
	}

	private static String effectiveTriggerName(Rule master, UUID triggerId, Map<UUID, String> triggerStagedNames) {
		return triggerStagedNames.getOrDefault(triggerId, master.getTriggerIngredient().asString());
	}

	private static RoleOrTechniqueImpl effectiveRole(Rule master, UUID roleId, Map<UUID, String> roleStagedNames) {
		if (roleId == null) {
			return null;
		}
		String masterName = master.getRoleOrTechnique() == null ? null : master.getRoleOrTechnique().asString();
		return new RoleOrTechniqueImpl(roleStagedNames.getOrDefault(roleId, masterName));
	}
}
