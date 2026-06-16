package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.v1.RuleChangeState;
import eu.dietwise.services.v1.StagedRule;
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
	private static final UUID RULE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String MASTER_RATIONALE = "Published rationale.";
	private static final String STAGED_RATIONALE = "Staged rationale, not yet published.";
	private static final Rule RULE = ImmutableRule.builder()
			.id(new GenericRuleId(RULE_ID.toString()))
			.recommendation(new RecommendationImpl("Decrease red meat"))
			.triggerIngredient(new TriggerIngredientImpl("Beef"))
			.roleOrTechnique(new RoleOrTechniqueImpl("minced in sauce"))
			.rationale(MASTER_RATIONALE)
			.build();

	@Mock
	private RuleDao ruleDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void listRulesReturnsUnchangedRuleWhenNothingIsStaged() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of()));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules).hasSize(1);
		assertThat(rules.get(0).rule()).isEqualTo(RULE);
		assertThat(rules.get(0).changeState()).isEqualTo(RuleChangeState.UNCHANGED);
		assertThat(rules.get(0).version()).isZero();
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRulesOverlaysStagedRationaleAndMarksItChanged() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of(RULE_ID, new StagedRuleOverlay(STAGED_RATIONALE, 4L))));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules).hasSize(1);
		assertThat(rules.get(0).rule().getRationale()).isEqualTo(STAGED_RATIONALE);
		assertThat(rules.get(0).rule().getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
		assertThat(rules.get(0).changeState()).isEqualTo(RuleChangeState.CHANGED);
		assertThat(rules.get(0).version()).isEqualTo(4L);
	}

	@Test
	void listRulesMarksRuleUnchangedWhenStagedRationaleEqualsMaster() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of(RULE_ID, new StagedRuleOverlay(MASTER_RATIONALE, 2L))));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules.get(0).changeState()).isEqualTo(RuleChangeState.UNCHANGED);
		assertThat(rules.get(0).version()).isEqualTo(2L);
	}

	@Test
	void listRulesRejectsAUserWithoutTheAdminRole() {
		assertThatThrownBy(() -> newService().listRules(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(ruleDao, never()).findAll(any(), any());
		verify(ruleDao, never()).findStagedOverlay(any());
	}

	@Test
	void listRulesRejectsAnUnauthenticatedUser() {
		assertThatThrownBy(() -> newService().listRules(unauthenticatedUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthenticatedException.class);
		verify(ruleDao, never()).findAll(any(), any());
	}

	@Test
	void stageRationaleStagesEditForAnAdminAndReturnsTheNewVersion() {
		when(ruleDao.stageRationale(any(), eq(RULE_ID), eq(STAGED_RATIONALE), eq(0L))).thenReturn(Uni.createFrom().item(1L));

		Long version = newService().stageRationale(adminUser(), new GenericRuleId(RULE_ID.toString()), STAGED_RATIONALE, 0L)
				.await().atMost(AWAIT);

		assertThat(version).isEqualTo(1L);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageRationaleRejectsAStaleBaseVersion() {
		when(ruleDao.stageRationale(any(), eq(RULE_ID), eq(STAGED_RATIONALE), eq(0L)))
				.thenReturn(Uni.createFrom().failure(new StaleVersionException(null, RULE_ID)));

		assertThatThrownBy(() -> newService().stageRationale(adminUser(), new GenericRuleId(RULE_ID.toString()), STAGED_RATIONALE, 0L)
				.await().atMost(AWAIT))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	void stageRationaleRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageRationale(nonAdminUser(), new GenericRuleId(RULE_ID.toString()), STAGED_RATIONALE, 0L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(ruleDao, never()).stageRationale(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private BackofficeRulesServiceImpl newService() {
		return new BackofficeRulesServiceImpl(ruleDao, persistenceContextFactory, new AuthorizationImpl());
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
