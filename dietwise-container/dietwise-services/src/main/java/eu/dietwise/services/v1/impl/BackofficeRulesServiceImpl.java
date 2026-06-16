package eu.dietwise.services.v1.impl;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
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
	public Uni<List<Rule>> listRules(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> ruleDao.findAll(em, RecipeLanguage.EN));
	}
}
