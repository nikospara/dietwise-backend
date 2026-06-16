package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.services.model.suggestions.StagedNewRule;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.RuleChangeState;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeRulesServiceImpl implements BackofficeRulesService {
	private final RuleDao ruleDao;
	private final RecommendationDao recommendationDao;
	private final TriggerIngredientDao triggerIngredientDao;
	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeRulesServiceImpl(
			RuleDao ruleDao,
			RecommendationDao recommendationDao,
			TriggerIngredientDao triggerIngredientDao,
			RoleOrTechniqueDao roleOrTechniqueDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.ruleDao = ruleDao;
		this.recommendationDao = recommendationDao;
		this.triggerIngredientDao = triggerIngredientDao;
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<List<StagedRule>> listRules(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				ruleDao.findAll(em, RecipeLanguage.EN),
				_ -> ruleDao.findStagedOverlay(em),
				_ -> ruleDao.findNewRules(em, RecipeLanguage.EN),
				BackofficeRulesServiceImpl::merge
		));
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
	public Uni<NewRuleOptions> newRuleOptions(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				recommendationDao.listOptions(em),
				_ -> triggerIngredientDao.findAll(em, RecipeLanguage.EN),
				_ -> roleOrTechniqueDao.findAll(em, RecipeLanguage.EN),
				(recommendations, triggerIngredients, rolesOrTechniques) ->
						new NewRuleOptions(
								recommendations,
								triggerIngredients.stream().map(triggerIngredient -> new ReferenceOption(triggerIngredient.getId().asUuid(), triggerIngredient.getName())).toList(),
								rolesOrTechniques.stream().map(roleOrTechnique -> new ReferenceOption(roleOrTechnique.getId().asUuid(), roleOrTechnique.getName())).toList()
						)
		));
	}

	private static List<StagedRule> merge(List<Rule> master, Map<UUID, StagedRuleOverlay> overlays, List<StagedNewRule> newRules) {
		Stream<StagedRule> published = master.stream().map(rule -> {
			StagedRuleOverlay overlay = overlays.get(rule.getId().asUuid());
			return overlay == null ? new StagedRule(rule, RuleChangeState.UNCHANGED, 0L) : applyOverlay(rule, overlay);
		});
		Stream<StagedRule> created = newRules.stream().map(newRule -> new StagedRule(newRule.rule(), RuleChangeState.NEW, newRule.version()));
		return Stream.concat(published, created).toList();
	}

	private static StagedRule applyOverlay(Rule master, StagedRuleOverlay overlay) {
		Rule effective = ImmutableRule.builder().from(master).rationale(overlay.rationale()).isActive(overlay.active()).build();
		boolean unchanged = Objects.equals(master.getRationale(), overlay.rationale()) && master.isActive() == overlay.active();
		return new StagedRule(effective, unchanged ? RuleChangeState.UNCHANGED : RuleChangeState.CHANGED, overlay.version());
	}
}
