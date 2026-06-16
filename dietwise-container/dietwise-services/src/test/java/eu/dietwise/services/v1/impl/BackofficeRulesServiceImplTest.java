package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackofficeRulesServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);
	private static final Rule RULE = ImmutableRule.builder()
			.id(new GenericRuleId("rule-1"))
			.recommendation(new RecommendationImpl("Decrease red meat"))
			.triggerIngredient(new TriggerIngredientImpl("Beef"))
			.roleOrTechnique(new RoleOrTechniqueImpl("minced in sauce"))
			.build();

	@Mock
	private RuleDao ruleDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void listRulesReturnsEveryRuleForAnAdminWithoutOpeningATransaction() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		var sut = new BackofficeRulesServiceImpl(ruleDao, persistenceContextFactory, new AuthorizationImpl());

		List<Rule> rules = sut.listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules).containsExactly(RULE);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRulesRejectsAUserWithoutTheAdminRole() {
		var sut = new BackofficeRulesServiceImpl(ruleDao, persistenceContextFactory, new AuthorizationImpl());

		assertThatThrownBy(() -> sut.listRules(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(ruleDao, never()).findAll(any(), any());
	}

	@Test
	void listRulesRejectsAnUnauthenticatedUser() {
		var sut = new BackofficeRulesServiceImpl(ruleDao, persistenceContextFactory, new AuthorizationImpl());

		assertThatThrownBy(() -> sut.listRules(unauthenticatedUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthenticatedException.class);
		verify(ruleDao, never()).findAll(any(), any());
	}

	private static User adminUser() {
		return user(EnumSet.of(Role.ADMIN), false);
	}

	private static User nonAdminUser() {
		return user(EnumSet.of(Role.CITIZEN), false);
	}

	private static User unauthenticatedUser() {
		return user(EnumSet.noneOf(Role.class), true);
	}

	private static User user(EnumSet<Role> roles, boolean unauthenticated) {
		return ImmutableUser.builder()
				.id(new UserIdImpl("00000000-0000-0000-0000-000000000009"))
				.name("editor@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(unauthenticated)
				.roles(roles)
				.build();
	}
}
