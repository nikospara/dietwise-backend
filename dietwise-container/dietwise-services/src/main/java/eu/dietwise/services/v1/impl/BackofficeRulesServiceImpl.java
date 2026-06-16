package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.RuleChangeState;
import eu.dietwise.services.v1.StagedRule;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeRulesServiceImpl implements BackofficeRulesService {
	private final RuleDao ruleDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeRulesServiceImpl(RuleDao ruleDao, ReactivePersistenceContextFactory persistenceContextFactory, Authorization authorization) {
		this.ruleDao = ruleDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<List<StagedRule>> listRules(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				ruleDao.findAll(em, RecipeLanguage.EN),
				_ -> ruleDao.findStagedOverlay(em),
				BackofficeRulesServiceImpl::merge
		));
	}

	@Override
	public Uni<Long> stageRationale(User user, RuleId ruleId, String rationale, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> ruleDao.stageRationale(tx, ruleId.asUuid(), rationale, baseVersion));
	}

	private static List<StagedRule> merge(List<Rule> master, Map<UUID, StagedRuleOverlay> overlays) {
		return master.stream()
				.map(rule -> {
					StagedRuleOverlay overlay = overlays.get(rule.getId().asUuid());
					return overlay == null ? new StagedRule(rule, RuleChangeState.UNCHANGED, 0L) : applyOverlay(rule, overlay);
				})
				.toList();
	}

	private static StagedRule applyOverlay(Rule master, StagedRuleOverlay overlay) {
		Rule effective = ImmutableRule.builder().from(master).rationale(overlay.rationale()).build();
		RuleChangeState changeState = Objects.equals(master.getRationale(), overlay.rationale())
				? RuleChangeState.UNCHANGED
				: RuleChangeState.CHANGED;
		return new StagedRule(effective, changeState, overlay.version());
	}
}
