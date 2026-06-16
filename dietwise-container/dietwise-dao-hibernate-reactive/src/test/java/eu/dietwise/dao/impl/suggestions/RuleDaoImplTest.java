package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueWcEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientWcEntity;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
class RuleDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID BEEF_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	private static final UUID MINCED_IN_SAUCE_RULE_ID = UUID.fromString("6629b06b-2756-4e26-ace8-95c1fda46cfe");
	private static final UUID DECREASE_RED_MEAT_RECOMMENDATION_ID = UUID.fromString("9977a713-0608-4f0c-9933-f7788b4f0225");
	private static final UUID ROLELESS_BEEF_RULE_ID = UUID.fromString("b1d5f3a2-0c44-4f7e-9a2b-7e1c9d6f0a01");
	private static final UUID PORK_MINCED_RULE_ID = UUID.fromString("79c845bb-ff9c-4a0e-9188-d2f9050f42c1");
	private static final UUID STAGING_RULE_ID = UUID.fromString("a2c4e6f8-1b3d-4e5f-8a9b-0c1d2e3f4a5b");
	private static final UUID STAGING_CONFLICT_RULE_ID = UUID.fromString("c7d8e9f0-2a3b-4c5d-9e8f-1a2b3c4d5e6f");
	private static final UUID REVERT_RULE_ID = UUID.fromString("d3e5f7a9-3c4d-4e6f-8b0c-1d2e3f4a5b6c");
	private static final UUID REVERT_CONFLICT_RULE_ID = UUID.fromString("e4f6a8b0-4d5e-4f7a-9c1d-2e3f4a5b6c7d");
	private static final UUID INACTIVE_BEEF_RULE_ID = UUID.fromString("f5a7b9c1-5e6f-4a8b-0d2e-3f4a5b6c7d8e");
	private static final UUID DEACTIVATE_RULE_ID = UUID.fromString("a6b8c0d2-6f7a-4b9c-1e3f-4a5b6c7d8e9f");
	private static final UUID ACTIVATE_RULE_ID = UUID.fromString("b7c9d1e3-7a8b-4c0d-2f4a-5b6c7d8e9f0a");
	private static final UUID MIXED_STAGE_RULE_ID = UUID.fromString("c8d0e2f4-8b9c-4d1e-3a5b-6c7d8e9f0a1b");
	private static final UUID REVERT_KEEPS_ACTIVE_RULE_ID = UUID.fromString("d9e1f3a5-9c0d-4e2f-4b6c-7d8e9f0a1b2c");
	private static final UUID SET_ACTIVE_CONFLICT_RULE_ID = UUID.fromString("e0f2a4b6-0d1e-4f3a-5c7d-8e9f0a1b2c3d");
	private static final UUID UNKNOWN_RULE_ID = UUID.fromString("f1a3b5c7-1e2f-4a3b-5c6d-7e8f9a0b1c2d");
	private static final UUID STALE_SEED_RULE_ID = UUID.fromString("a2b4c6d8-2f3a-4b5c-6d7e-8f9a0b1c2d3e");
	private static final UUID NOOP_ACTIVE_RULE_ID = UUID.fromString("b3c5d7e9-3a4b-4c5d-7e8f-9a0b1c2d3e4f");
	private static final UUID NEW_RULE_ROLE_ID = UUID.fromString("c4d6e8f0-4b5c-4d6e-8f9a-0b1c2d3e4f50");
	private static final String NEW_RULE_ROLE_NAME = "Centerpiece for a new rule";
	private static final UUID BK_MASTER_RULE_ID = UUID.fromString("d5e7f9a1-5c6d-4e7f-9a0b-1c2d3e4f5061");
	private static final UUID BK_NEW_ROLE_ID = UUID.fromString("e6f8a0b2-6d7e-4f8a-0b1c-2d3e4f506172");
	private static final UUID DISCARD_ROLE_ID = UUID.fromString("f7a9b1c3-7e8f-4a9b-1c2d-3e4f50617283");
	private static final UUID DISCARD_STALE_ROLE_ID = UUID.fromString("a8b0c2d4-8f9a-4b0c-2d3e-4f5061728394");
	private static final UUID DISCARD_PUBLISHED_RULE_ID = UUID.fromString("b9c1d3e5-9a0b-4c1d-3e4f-5061728394a5");
	private static final UUID WC_TRIGGER_ID = UUID.fromString("c0d2e4f6-0b1c-4d2e-4f50-61728394a5b6");
	private static final String WC_TRIGGER_NAME = "Working-copy-only trigger";
	private static final UUID WC_ROLE_ID = UUID.fromString("d1e3f5a7-1c2d-4e3f-5061-728394a5b6c7");
	private static final String WC_ROLE_NAME = "Working-copy-only role";
	private static final String STAGING_RULE_MASTER_RATIONALE = "Published master rationale.";
	private static final String STAGED_RATIONALE = "Staged rationale, not yet published.";
	private static final String RESTAGED_RATIONALE = "Re-staged rationale after reload.";

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void testFindByTriggerIngredient(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> tx.find(RuleEntity.class, MINCED_IN_SAUCE_RULE_ID)
						.invoke(rule -> rule.setRationale("Use this when the ingredient is minced and cooked into a sauce.")))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var rules = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID), RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).hasSize(3);
		assertThat(rules).allSatisfy(suggestion -> {
			assertThat(suggestion.getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
			assertThat(suggestion.getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
		});
		assertThat(rules.stream().map(Rule::getRoleOrTechnique).map(RepresentableAsString::asString).collect(Collectors.toSet()))
				.containsExactlyInAnyOrder("minced in sauce", "cubes stew", "steak centerpiece");
		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(MINCED_IN_SAUCE_RULE_ID.toString());
			assertThat(rule.getRationale()).isEqualTo("Use this when the ingredient is minced and cooked into a sauce.");
		});
	}

	@Test
	@Order(2)
	void testFindByTriggerIngredientReturnsLocalizedRationale(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var rules = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID), RecipeLanguage.NL)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(MINCED_IN_SAUCE_RULE_ID.toString());
			assertThat(rule.getRationale()).isEqualTo("Biedt plantaardige texturen die goed integreren in sauzen met behoud van hartige diepte.");
		});
	}

	@Test
	@Order(3)
	void testFindByTriggerIngredientIncludesRuleWithoutRoleOrTechnique(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var rule = new RuleEntity();
			rule.setId(ROLELESS_BEEF_RULE_ID);
			rule.setRecommendation(tx.getReference(RecommendationEntity.class, DECREASE_RED_MEAT_RECOMMENDATION_ID));
			rule.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, BEEF_ID));
			rule.setRoleOrTechnique(null);
			rule.setRationale("Applies to beef regardless of how it is prepared.");
			return tx.persist(rule);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var rules = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID), RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(ROLELESS_BEEF_RULE_ID.toString());
			assertThat(rule.getRoleOrTechnique()).isNull();
			assertThat(rule.getRationale()).isEqualTo("Applies to beef regardless of how it is prepared.");
		});
	}

	@Test
	@Order(4)
	void testFindAllReturnsRulesAcrossTriggerIngredients(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var rules = factory.withoutTransaction(em ->
				sut.findAll(em, RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(MINCED_IN_SAUCE_RULE_ID.toString());
			assertThat(rule.getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
		});
		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(PORK_MINCED_RULE_ID.toString());
			assertThat(rule.getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Pork"));
		});
		assertThat(rules.stream().map(rule -> rule.getTriggerIngredient().asString()).distinct().count())
				.isGreaterThan(1);
	}

	@Test
	@Order(5)
	void testStageRationaleStoresInWorkingCopyLeavingMasterUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, STAGING_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newVersion = factory.withTransaction(tx -> sut.stageRationale(tx, STAGING_RULE_ID, STAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newVersion).isEqualTo(1L);

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(STAGING_RULE_ID);
		assertThat(overlay.get(STAGING_RULE_ID).rationale()).isEqualTo(STAGED_RATIONALE);
		assertThat(overlay.get(STAGING_RULE_ID).version()).isEqualTo(1L);

		var master = factory.withTransaction(tx -> tx.find(RuleEntity.class, STAGING_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.getRationale()).isEqualTo(STAGING_RULE_MASTER_RATIONALE);
	}

	@Test
	@Order(6)
	void testStageRationaleRejectsStaleBaseVersionThenAcceptsCurrent(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, STAGING_CONFLICT_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageRationale(tx, STAGING_CONFLICT_RULE_ID, STAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageRationale(tx, STAGING_CONFLICT_RULE_ID, RESTAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var newVersion = factory.withTransaction(tx -> sut.stageRationale(tx, STAGING_CONFLICT_RULE_ID, RESTAGED_RATIONALE, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newVersion).isEqualTo(2L);

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay.get(STAGING_CONFLICT_RULE_ID).rationale()).isEqualTo(RESTAGED_RATIONALE);
		assertThat(overlay.get(STAGING_CONFLICT_RULE_ID).version()).isEqualTo(2L);
	}

	@Test
	@Order(7)
	void testRevertRationaleRemovesWorkingCopyRowLeavingMasterUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, REVERT_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var stagedVersion = factory.withTransaction(tx -> sut.stageRationale(tx, REVERT_RULE_ID, STAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedVersion).isEqualTo(1L);

		factory.withTransaction(tx -> sut.revertRationale(tx, REVERT_RULE_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(REVERT_RULE_ID);

		var master = factory.withTransaction(tx -> tx.find(RuleEntity.class, REVERT_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.getRationale()).isEqualTo(STAGING_RULE_MASTER_RATIONALE);
	}

	@Test
	@Order(8)
	void testRevertRationaleRejectsStaleBaseVersionLeavingTheStagedRowIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, REVERT_CONFLICT_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageRationale(tx, REVERT_CONFLICT_RULE_ID, STAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertRationale(tx, REVERT_CONFLICT_RULE_ID, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillStaged = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillStaged.get(REVERT_CONFLICT_RULE_ID).rationale()).isEqualTo(STAGED_RATIONALE);
		assertThat(stillStaged.get(REVERT_CONFLICT_RULE_ID).version()).isEqualTo(1L);

		factory.withTransaction(tx -> sut.revertRationale(tx, REVERT_CONFLICT_RULE_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var afterRevert = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(afterRevert).doesNotContainKey(REVERT_CONFLICT_RULE_ID);
	}

	@Test
	@Order(9)
	void testFindByTriggerIngredientExcludesInactiveRulesButFindAllKeepsThem(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, INACTIVE_BEEF_RULE_ID, "Inactive beef rule.", false))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var assessed = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID), RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(assessed).noneSatisfy(rule ->
				assertThat(rule.getId().asString()).isEqualTo(INACTIVE_BEEF_RULE_ID.toString()));
		assertThat(assessed).anySatisfy(rule ->
				assertThat(rule.getId().asString()).isEqualTo(MINCED_IN_SAUCE_RULE_ID.toString()));

		var all = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(all).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(INACTIVE_BEEF_RULE_ID.toString());
			assertThat(rule.isActive()).isFalse();
		});
	}

	@Test
	@Order(10)
	void testSetActiveStagesDeactivationLeavingMasterUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, DEACTIVATE_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.setActive(tx, DEACTIVATE_RULE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(DEACTIVATE_RULE_ID);
		assertThat(overlay.get(DEACTIVATE_RULE_ID).active()).isFalse();
		assertThat(overlay.get(DEACTIVATE_RULE_ID).rationale()).isEqualTo(STAGING_RULE_MASTER_RATIONALE);
		assertThat(overlay.get(DEACTIVATE_RULE_ID).version()).isEqualTo(1L);

		var master = factory.withTransaction(tx -> tx.find(RuleEntity.class, DEACTIVATE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.isActive()).isTrue();
	}

	@Test
	@Order(11)
	void testSetActiveBackToMasterCollapsesTheWorkingCopyRow(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, ACTIVATE_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.setActive(tx, ACTIVATE_RULE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.setActive(tx, ACTIVATE_RULE_ID, true, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(ACTIVATE_RULE_ID);
	}

	@Test
	@Order(12)
	void testSetActiveKeepsRowWhenRationaleStillDiffersFromMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, MIXED_STAGE_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageRationale(tx, MIXED_STAGE_RULE_ID, STAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.setActive(tx, MIXED_STAGE_RULE_ID, false, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.setActive(tx, MIXED_STAGE_RULE_ID, true, 2L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(MIXED_STAGE_RULE_ID);
		assertThat(overlay.get(MIXED_STAGE_RULE_ID).active()).isTrue();
		assertThat(overlay.get(MIXED_STAGE_RULE_ID).rationale()).isEqualTo(STAGED_RATIONALE);
		assertThat(overlay.get(MIXED_STAGE_RULE_ID).version()).isEqualTo(3L);
	}

	@Test
	@Order(13)
	void testRevertRationaleKeepsRowWhenDeactivationStillStaged(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, REVERT_KEEPS_ACTIVE_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.setActive(tx, REVERT_KEEPS_ACTIVE_RULE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageRationale(tx, REVERT_KEEPS_ACTIVE_RULE_ID, STAGED_RATIONALE, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertRationale(tx, REVERT_KEEPS_ACTIVE_RULE_ID, 2L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(REVERT_KEEPS_ACTIVE_RULE_ID);
		assertThat(overlay.get(REVERT_KEEPS_ACTIVE_RULE_ID).rationale()).isEqualTo(STAGING_RULE_MASTER_RATIONALE);
		assertThat(overlay.get(REVERT_KEEPS_ACTIVE_RULE_ID).active()).isFalse();
		assertThat(overlay.get(REVERT_KEEPS_ACTIVE_RULE_ID).version()).isEqualTo(3L);
	}

	@Test
	@Order(14)
	void testSetActiveRejectsStaleBaseVersionLeavingTheStagedRowIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, SET_ACTIVE_CONFLICT_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.setActive(tx, SET_ACTIVE_CONFLICT_RULE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.setActive(tx, SET_ACTIVE_CONFLICT_RULE_ID, true, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay.get(SET_ACTIVE_CONFLICT_RULE_ID).active()).isFalse();
		assertThat(overlay.get(SET_ACTIVE_CONFLICT_RULE_ID).version()).isEqualTo(1L);
	}

	@Test
	@Order(15)
	void testSetActiveOnAnUnknownRuleFailsWithEntityNotFound(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.setActive(tx, UNKNOWN_RULE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(UNKNOWN_RULE_ID);
	}

	@Test
	@Order(16)
	void testSetActiveWithNonZeroBaseVersionOnAnUnstagedRuleIsStale(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, STALE_SEED_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.setActive(tx, STALE_SEED_RULE_ID, false, 5L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(STALE_SEED_RULE_ID);
	}

	@Test
	@Order(17)
	void testSetActiveToTheMasterValueOnAnUnstagedRuleIsANoOp(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, NOOP_ACTIVE_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.setActive(tx, NOOP_ACTIVE_RULE_ID, true, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(NOOP_ACTIVE_RULE_ID);
	}

	@Test
	@Order(18)
	void testCreateRuleStagesAMirrorOnlyRuleResolvedByFindNewRules(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRole(tx, NEW_RULE_ROLE_ID, NEW_RULE_ROLE_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newId = factory.withTransaction(tx -> sut.createRule(tx, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, NEW_RULE_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newRules = factory.withoutTransaction(em -> sut.findNewRules(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newRules).anySatisfy(staged -> {
			assertThat(staged.rule().getId().asString()).isEqualTo(newId.toString());
			assertThat(staged.rule().getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
			assertThat(staged.rule().getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
			assertThat(staged.rule().getRoleOrTechnique().asString()).isEqualTo(NEW_RULE_ROLE_NAME);
			assertThat(staged.rule().getRationale()).isNull();
			assertThat(staged.rule().isActive()).isTrue();
			assertThat(staged.version()).isEqualTo(1L);
		});

		var all = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(all).noneSatisfy(rule -> assertThat(rule.getId().asString()).isEqualTo(newId.toString()));
	}

	@Test
	@Order(19)
	void testFindBusinessKeysIncludesMasterAndWorkingCopyTriplets(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, BK_MASTER_RULE_ID, "A master rule for the business-key test."))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> persistRole(tx, BK_NEW_ROLE_ID, "Role for the business-key new rule"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.createRule(tx, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, BK_NEW_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var keys = factory.withoutTransaction(sut::findBusinessKeys)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(keys).contains(new RuleBusinessKey(DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, BK_MASTER_RULE_ID));
		assertThat(keys).contains(new RuleBusinessKey(DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, BK_NEW_ROLE_ID));
	}

	@Test
	@Order(20)
	void testCreateRuleWithNoRoleResolvesToANullRole(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createRule(tx, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, null))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newRules = factory.withoutTransaction(em -> sut.findNewRules(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newRules).anySatisfy(staged -> {
			assertThat(staged.rule().getId().asString()).isEqualTo(newId.toString());
			assertThat(staged.rule().getRoleOrTechnique()).isNull();
		});
	}

	@Test
	@Order(21)
	void testDiscardNewRuleRemovesItFromTheWorkingCopy(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRole(tx, DISCARD_ROLE_ID, "Role for the discard test"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var newId = factory.withTransaction(tx -> sut.createRule(tx, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, DISCARD_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var before = factory.withoutTransaction(em -> sut.findNewRules(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(before).anySatisfy(staged -> assertThat(staged.rule().getId().asString()).isEqualTo(newId.toString()));

		factory.withTransaction(tx -> sut.discardNewRule(tx, newId, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(em -> sut.findNewRules(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after).noneSatisfy(staged -> assertThat(staged.rule().getId().asString()).isEqualTo(newId.toString()));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(newId);
	}

	@Test
	@Order(22)
	void testDiscardNewRuleRejectsStaleBaseVersionLeavingItIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRole(tx, DISCARD_STALE_ROLE_ID, "Role for the discard stale test"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var newId = factory.withTransaction(tx -> sut.createRule(tx, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, DISCARD_STALE_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.discardNewRule(tx, newId, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillThere = factory.withoutTransaction(em -> sut.findNewRules(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillThere).anySatisfy(staged -> assertThat(staged.rule().getId().asString()).isEqualTo(newId.toString()));
	}

	@Test
	@Order(23)
	void testDiscardRefusesAPublishedRuleLeavingItsStagedChangeIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRule(tx, DISCARD_PUBLISHED_RULE_ID, STAGING_RULE_MASTER_RATIONALE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageRationale(tx, DISCARD_PUBLISHED_RULE_ID, STAGED_RATIONALE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.discardNewRule(tx, DISCARD_PUBLISHED_RULE_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(DISCARD_PUBLISHED_RULE_ID);
		assertThat(overlay.get(DISCARD_PUBLISHED_RULE_ID).rationale()).isEqualTo(STAGED_RATIONALE);
	}

	@Test
	@Order(24)
	void testDiscardOnARuleWithNoWorkingCopyRowIsANoOp(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.discardNewRule(tx, UNKNOWN_RULE_ID, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(sut::findStagedOverlay)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(UNKNOWN_RULE_ID);
	}

	@Test
	@Order(25)
	void testFindNewRulesResolvesReferencesToWorkingCopyOnlyEntities(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var trigger = new TriggerIngredientWcEntity();
			trigger.setId(WC_TRIGGER_ID);
			trigger.setName(WC_TRIGGER_NAME);
			trigger.setVersion(1L);
			var role = new RoleOrTechniqueWcEntity();
			role.setId(WC_ROLE_ID);
			role.setName(WC_ROLE_NAME);
			role.setVersion(1L);
			return tx.persist(trigger).chain(() -> tx.persist(role));
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newId = factory.withTransaction(tx -> sut.createRule(tx, DECREASE_RED_MEAT_RECOMMENDATION_ID, WC_TRIGGER_ID, WC_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newRules = factory.withoutTransaction(em -> sut.findNewRules(em, RecipeLanguage.EN))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newRules).anySatisfy(staged -> {
			assertThat(staged.rule().getId().asString()).isEqualTo(newId.toString());
			assertThat(staged.rule().getTriggerIngredient().asString()).isEqualTo(WC_TRIGGER_NAME);
			assertThat(staged.rule().getRoleOrTechnique().asString()).isEqualTo(WC_ROLE_NAME);
		});
	}

	private static Uni<Void> persistRole(ReactivePersistenceTxContext tx, UUID id, String name) {
		var role = new RoleOrTechniqueEntity();
		role.setId(id);
		role.setName(name);
		return tx.persist(role).replaceWithVoid();
	}

	private static Uni<Void> createRule(ReactivePersistenceTxContext tx, UUID id, String rationale) {
		return createRule(tx, id, rationale, true);
	}

	private static Uni<Void> createRule(ReactivePersistenceTxContext tx, UUID id, String rationale, boolean active) {
		var role = new RoleOrTechniqueEntity();
		role.setId(id);
		role.setName("Role for rule " + id);
		var rule = new RuleEntity();
		rule.setId(id);
		rule.setRecommendation(tx.getReference(RecommendationEntity.class, DECREASE_RED_MEAT_RECOMMENDATION_ID));
		rule.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, BEEF_ID));
		rule.setRoleOrTechnique(role);
		rule.setRationale(rationale);
		rule.setActive(active);
		return tx.persist(role).chain(() -> tx.persist(rule)).replaceWithVoid();
	}
}
