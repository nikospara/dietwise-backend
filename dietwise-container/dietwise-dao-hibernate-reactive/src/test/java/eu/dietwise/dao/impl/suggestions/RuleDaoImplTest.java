package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
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

	private static Uni<Void> createRule(ReactivePersistenceTxContext tx, UUID id, String rationale) {
		var rule = new RuleEntity();
		rule.setId(id);
		rule.setRecommendation(tx.getReference(RecommendationEntity.class, DECREASE_RED_MEAT_RECOMMENDATION_ID));
		rule.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, BEEF_ID));
		rule.setRoleOrTechnique(null);
		rule.setRationale(rationale);
		return tx.persist(rule).replaceWithVoid();
	}
}
