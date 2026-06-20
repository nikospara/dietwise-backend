package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.services.model.suggestions.RuleReferences;
import eu.dietwise.services.model.suggestions.StagedNewRule;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.RuleChangeState;
import eu.dietwise.services.v1.types.RuleField;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeRulesServiceImpl implements BackofficeRulesService {
	private final RuleDao ruleDao;
	private final RecommendationDao recommendationDao;
	private final TriggerIngredientDao triggerIngredientDao;
	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final SuggestionTemplateDao suggestionTemplateDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeRulesServiceImpl(
			RuleDao ruleDao,
			RecommendationDao recommendationDao,
			TriggerIngredientDao triggerIngredientDao,
			RoleOrTechniqueDao roleOrTechniqueDao,
			SuggestionTemplateDao suggestionTemplateDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.ruleDao = ruleDao;
		this.recommendationDao = recommendationDao;
		this.triggerIngredientDao = triggerIngredientDao;
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.suggestionTemplateDao = suggestionTemplateDao;
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

	private Uni<TranslationCompleteness> translationCompleteness(ReactivePersistenceContext em) {
		return forcm(
				ruleDao.findRationaleTranslationLangs(em),
				_ -> triggerIngredientDao.findTranslationLangs(em),
				(_, _) -> roleOrTechniqueDao.findTranslationLangs(em),
				TranslationCompleteness::new
		);
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
	public Uni<Map<RecipeLanguage, VersionedText>> rationaleTranslationsForEdit(User user, RuleId ruleId) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> ruleDao.findRationaleTranslationsForEdit(em, ruleId.asUuid()));
	}

	@Override
	public Uni<Void> stageRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, String rationale, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.stageRationaleTranslation(tx, ruleId.asUuid(), lang, rationale, baseVersion));
	}

	@Override
	public Uni<Void> revertRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.revertRationaleTranslation(tx, ruleId.asUuid(), lang, baseVersion));
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
				BackofficeTranslations.translationStates(master.getId().asUuid(), completeness.rationale()),
				BackofficeTranslations.translationStates(triggerId, completeness.triggerIngredient()),
				roleId == null ? Map.of() : BackofficeTranslations.translationStates(roleId, completeness.roleOrTechnique()),
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
				BackofficeTranslations.translationStates(newRule.rule().getId().asUuid(), completeness.rationale()),
				BackofficeTranslations.translationStates(triggerId, completeness.triggerIngredient()),
				roleId == null ? Map.of() : BackofficeTranslations.translationStates(roleId, completeness.roleOrTechnique()),
				newRule.version());
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
