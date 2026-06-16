package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.services.model.suggestions.StagedNewRule;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.types.suggestions.RoleOrTechniqueId;
import eu.dietwise.services.types.suggestions.TriggerIngredientId;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.RuleChangeState;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
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
	private static final UUID NEW_RULE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID RECOMMENDATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID ROLE_OR_TECHNIQUE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
	private static final String MASTER_RATIONALE = "Published rationale.";
	private static final String STAGED_RATIONALE = "Staged rationale, not yet published.";
	private static final Rule RULE = ImmutableRule.builder()
			.id(new GenericRuleId(RULE_ID.toString()))
			.recommendation(new RecommendationImpl("Decrease red meat"))
			.triggerIngredient(new TriggerIngredientImpl("Beef"))
			.roleOrTechnique(new RoleOrTechniqueImpl("minced in sauce"))
			.rationale(MASTER_RATIONALE)
			.build();
	private static final Rule NEW_RULE = ImmutableRule.builder()
			.id(new GenericRuleId(NEW_RULE_ID.toString()))
			.recommendation(new RecommendationImpl("Decrease sodium"))
			.triggerIngredient(new TriggerIngredientImpl("Soy sauce"))
			.roleOrTechnique(new RoleOrTechniqueImpl("seasoning"))
			.rationale(null)
			.build();

	@Mock
	private RuleDao ruleDao;

	@Mock
	private RecommendationDao recommendationDao;

	@Mock
	private TriggerIngredientDao triggerIngredientDao;

	@Mock
	private RoleOrTechniqueDao roleOrTechniqueDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void listRulesReturnsUnchangedRuleWhenNothingIsStaged() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findNewRules(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of()));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of()));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules).hasSize(1);
		assertThat(rules.getFirst().rule()).isEqualTo(RULE);
		assertThat(rules.getFirst().changeState()).isEqualTo(RuleChangeState.UNCHANGED);
		assertThat(rules.getFirst().version()).isZero();
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRulesOverlaysStagedRationaleAndMarksItChanged() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findNewRules(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of()));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of(RULE_ID, new StagedRuleOverlay(STAGED_RATIONALE, true, 4L))));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules).hasSize(1);
		assertThat(rules.getFirst().rule().getRationale()).isEqualTo(STAGED_RATIONALE);
		assertThat(rules.getFirst().rule().getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
		assertThat(rules.getFirst().changeState()).isEqualTo(RuleChangeState.CHANGED);
		assertThat(rules.getFirst().version()).isEqualTo(4L);
	}

	@Test
	void listRulesMarksRuleUnchangedWhenStagedRationaleEqualsMaster() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findNewRules(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of()));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of(RULE_ID, new StagedRuleOverlay(MASTER_RATIONALE, true, 2L))));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules.getFirst().changeState()).isEqualTo(RuleChangeState.UNCHANGED);
		assertThat(rules.getFirst().version()).isEqualTo(2L);
	}

	@Test
	void listRulesMarksRuleChangedAndInactiveWhenDeactivationStaged() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findNewRules(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of()));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of(RULE_ID, new StagedRuleOverlay(MASTER_RATIONALE, false, 5L))));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules.getFirst().rule().getRationale()).isEqualTo(MASTER_RATIONALE);
		assertThat(rules.getFirst().rule().isActive()).isFalse();
		assertThat(rules.getFirst().changeState()).isEqualTo(RuleChangeState.CHANGED);
		assertThat(rules.getFirst().version()).isEqualTo(5L);
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

	@Test
	void revertRationaleRemovesTheStagedChangeForAnAdmin() {
		when(ruleDao.revertRationale(any(), eq(RULE_ID), eq(1L))).thenReturn(Uni.createFrom().voidItem());

		newService().revertRationale(adminUser(), new GenericRuleId(RULE_ID.toString()), 1L).await().atMost(AWAIT);

		verify(ruleDao).revertRationale(any(), eq(RULE_ID), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertRationaleRejectsAStaleBaseVersion() {
		when(ruleDao.revertRationale(any(), eq(RULE_ID), eq(1L)))
				.thenReturn(Uni.createFrom().failure(new StaleVersionException(null, RULE_ID)));

		assertThatThrownBy(() -> newService().revertRationale(adminUser(), new GenericRuleId(RULE_ID.toString()), 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	void revertRationaleRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertRationale(nonAdminUser(), new GenericRuleId(RULE_ID.toString()), 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(ruleDao, never()).revertRationale(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void setActiveStagesDeactivationForAnAdmin() {
		when(ruleDao.setActive(any(), eq(RULE_ID), eq(false), eq(1L))).thenReturn(Uni.createFrom().voidItem());

		newService().setActive(adminUser(), new GenericRuleId(RULE_ID.toString()), false, 1L).await().atMost(AWAIT);

		verify(ruleDao).setActive(any(), eq(RULE_ID), eq(false), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void setActiveRejectsAStaleBaseVersion() {
		when(ruleDao.setActive(any(), eq(RULE_ID), eq(true), eq(1L)))
				.thenReturn(Uni.createFrom().failure(new StaleVersionException(null, RULE_ID)));

		assertThatThrownBy(() -> newService().setActive(adminUser(), new GenericRuleId(RULE_ID.toString()), true, 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	void setActiveRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().setActive(nonAdminUser(), new GenericRuleId(RULE_ID.toString()), false, 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(ruleDao, never()).setActive(any(), any(), anyBoolean(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRulesAppendsWorkingCopyOnlyRulesAsNewState() {
		when(ruleDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(RULE)));
		when(ruleDao.findStagedOverlay(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(ruleDao.findNewRules(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(new StagedNewRule(NEW_RULE, 1L))));

		List<StagedRule> rules = newService().listRules(adminUser()).await().atMost(AWAIT);

		assertThat(rules).hasSize(2);
		assertThat(rules).anySatisfy(staged -> {
			assertThat(staged.rule()).isEqualTo(RULE);
			assertThat(staged.changeState()).isEqualTo(RuleChangeState.UNCHANGED);
		});
		assertThat(rules).anySatisfy(staged -> {
			assertThat(staged.rule()).isEqualTo(NEW_RULE);
			assertThat(staged.changeState()).isEqualTo(RuleChangeState.NEW);
			assertThat(staged.version()).isEqualTo(1L);
		});
	}

	@Test
	void createRuleStagesANewRuleForAnAdmin() {
		when(ruleDao.findBusinessKeys(any())).thenReturn(Uni.createFrom().item(Set.of()));
		when(ruleDao.createRule(any(), eq(RECOMMENDATION_ID), eq(TRIGGER_INGREDIENT_ID), eq(ROLE_OR_TECHNIQUE_ID)))
				.thenReturn(Uni.createFrom().item(NEW_RULE_ID));

		RuleId id = newService().createRule(adminUser(), RECOMMENDATION_ID, TRIGGER_INGREDIENT_ID, ROLE_OR_TECHNIQUE_ID)
				.await().atMost(AWAIT);

		assertThat(id.asString()).isEqualTo(NEW_RULE_ID.toString());
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void createRuleRejectsADuplicateBusinessKeyWithoutCreating() {
		when(ruleDao.findBusinessKeys(any())).thenReturn(Uni.createFrom().item(
				Set.of(new RuleBusinessKey(RECOMMENDATION_ID, TRIGGER_INGREDIENT_ID, ROLE_OR_TECHNIQUE_ID))));

		assertThatThrownBy(() -> newService().createRule(adminUser(), RECOMMENDATION_ID, TRIGGER_INGREDIENT_ID, ROLE_OR_TECHNIQUE_ID)
				.await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(ruleDao, never()).createRule(any(), any(), any(), any());
	}

	@Test
	void createRuleRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().createRule(nonAdminUser(), RECOMMENDATION_ID, TRIGGER_INGREDIENT_ID, ROLE_OR_TECHNIQUE_ID)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(ruleDao, never()).findBusinessKeys(any());
		verify(ruleDao, never()).createRule(any(), any(), any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void newRuleOptionsReturnsMappedReferenceDataForAnAdmin() {
		when(recommendationDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(
				new ReferenceOption(RECOMMENDATION_ID, "Decrease sodium"))));
		TriggerIngredientId triggerIngredientId = mock(TriggerIngredientId.class);
		when(triggerIngredientId.asUuid()).thenReturn(TRIGGER_INGREDIENT_ID);
		TriggerIngredient triggerIngredient = mock(TriggerIngredient.class);
		when(triggerIngredient.getId()).thenReturn(triggerIngredientId);
		when(triggerIngredient.getName()).thenReturn("Soy sauce");
		when(triggerIngredientDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(triggerIngredient)));
		RoleOrTechniqueId roleOrTechniqueId = mock(RoleOrTechniqueId.class);
		when(roleOrTechniqueId.asUuid()).thenReturn(ROLE_OR_TECHNIQUE_ID);
		RoleOrTechnique roleOrTechnique = mock(RoleOrTechnique.class);
		when(roleOrTechnique.getId()).thenReturn(roleOrTechniqueId);
		when(roleOrTechnique.getName()).thenReturn("seasoning");
		when(roleOrTechniqueDao.findAll(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(List.of(roleOrTechnique)));

		NewRuleOptions options = newService().newRuleOptions(adminUser()).await().atMost(AWAIT);

		assertThat(options.recommendations()).containsExactly(new ReferenceOption(RECOMMENDATION_ID, "Decrease sodium"));
		assertThat(options.triggerIngredients()).containsExactly(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Soy sauce"));
		assertThat(options.rolesOrTechniques()).containsExactly(new ReferenceOption(ROLE_OR_TECHNIQUE_ID, "seasoning"));
	}

	@Test
	void newRuleOptionsRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().newRuleOptions(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).listOptions(any());
	}

	private BackofficeRulesServiceImpl newService() {
		return new BackofficeRulesServiceImpl(ruleDao, recommendationDao, triggerIngredientDao, roleOrTechniqueDao, persistenceContextFactory, new AuthorizationImpl());
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
