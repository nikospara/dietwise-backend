package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SuggestionTemplateDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID DECREASE_RED_MEAT_RECOMMENDATION_ID = UUID.fromString("9977a713-0608-4f0c-9933-f7788b4f0225");
	private static final UUID BEEF_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	private static final UUID MINCED_IN_SAUCE_RULE_ID = UUID.fromString("6629b06b-2756-4e26-ace8-95c1fda46cfe");
	private static final UUID EMPTY_RULE_ID = UUID.fromString("c1c1c1c1-0000-4000-8000-000000000001");

	private static final UUID FIRST_TEMPLATE_ID = UUID.fromString("90984ed6-5e6b-4381-ac0d-92b6dd3cf503");
	private static final String BROWN_LENTILS = "Brown lentils (cooked)";
	private static final String SOY_MINCE = "Soy mince";
	private static final String MUSHROOM_LENTIL_MIX = "Mushroom–lentil mix";
	private static final String FIRST_RESTRICTION = "Not for burgers without binder";
	private static final String SECOND_RESTRICTION = "Avoid if soy allergy";
	private static final String THIRD_RESTRICTION = "Use only in sauces";
	private static final String SHARED_EQUIVALENCE = "1:1 (200g beef → 200g cooked lentils) or 200g beef → 120g lentils + 150g mushrooms";
	private static final String SHARED_TECHNIQUE_NOTES = "Finely chop mushrooms; dry sauté before adding";

	// Seeded templates used for staging; each test uses a distinct template so the shared Working Copy stays isolated.
	private static final UUID SEITAN_RULE_ID = UUID.fromString("86b9386f-7b10-4d54-8278-c5d86c6986ff");
	private static final UUID STAGE_TEMPLATE_ID = UUID.fromString("153ff634-5f6d-44a7-90d7-4080329ab4fd");
	private static final String STAGE_MASTER_RESTRICTION = "Avoid if gluten-free";
	private static final String STAGE_MASTER_EQUIVALENCE = "1:1 by weight";
	private static final String STAGE_MASTER_TECHNIQUE_NOTES = "Simmer longer for seitan; coat tofu in starch before braise";
	private static final String STAGED_RESTRICTION = "Avoid if gluten-free or coeliac";
	private static final UUID STALE_TEMPLATE_ID = UUID.fromString("c9601a0e-515a-47b5-82a6-6007e3950a5a");
	private static final String FIRST_STAGED_EQUIVALENCE = "1:1 by volume";
	private static final String SECOND_STAGED_EQUIVALENCE = "1:1.2 by weight";

	private static final UUID SEAR_RULE_ID = UUID.fromString("f5108072-08c1-490a-b29c-877e987af2c4");
	private static final UUID REVERT_TEMPLATE_ID = UUID.fromString("faa27b53-6696-4178-8116-1ddccc63cb81");
	private static final String REVERT_MASTER_RESTRICTION = "Avoid if gluten-free";
	private static final UUID KEEP_TEMPLATE_ID = UUID.fromString("f9d8fdf0-89bc-4a50-a84d-ad4d0061a692");
	private static final String KEEP_MASTER_RESTRICTION = "Press 20 min before searing";
	private static final String KEEP_STAGED_RESTRICTION = "Press 30 min before searing";
	private static final String KEEP_STAGED_EQUIVALENCE = "2 pieces per 200g";

	private static final UUID STALE_REVERT_RULE_ID = UUID.fromString("79c845bb-ff9c-4a0e-9188-d2f9050f42c1");
	private static final UUID STALE_REVERT_TEMPLATE_ID = UUID.fromString("64f02c55-1763-4940-bfb8-31acadfc40e3");
	private static final String STALE_REVERT_STAGED_RESTRICTION = "Avoid if soy or nut allergy";

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
	void findByRuleReturnsTemplatesOrderedByAlternativeOrderWithIngredientNamesAndFields(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var templates = factory.withoutTransaction(em -> sut.findByRule(em, MINCED_IN_SAUCE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(templates).hasSize(3);
		assertThat(templates).extracting(template -> template.getAlternative().asString())
				.containsExactly(BROWN_LENTILS, SOY_MINCE, MUSHROOM_LENTIL_MIX);
		assertThat(templates).extracting(template -> template.getRestriction().orElse(null))
				.containsExactly(FIRST_RESTRICTION, SECOND_RESTRICTION, THIRD_RESTRICTION);
		assertThat(templates.getFirst()).isEqualTo(ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(FIRST_TEMPLATE_ID.toString()))
				.alternative(new AlternativeIngredientImpl(BROWN_LENTILS))
				.restriction(Optional.of(FIRST_RESTRICTION))
				.equivalence(Optional.of(SHARED_EQUIVALENCE))
				.techniqueNotes(Optional.of(SHARED_TECHNIQUE_NOTES))
				.build());
	}

	@Test
	void findByRuleReturnsAnEmptyListForARuleWithNoTemplates(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRuleWithoutTemplates(tx, EMPTY_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var templates = factory.withoutTransaction(em -> sut.findByRule(em, EMPTY_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(templates).isEmpty();
	}

	@Test
	void stageFieldStoresTheEditInTheWorkingCopyLeavingMasterUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newVersion = factory
				.withTransaction(tx -> sut.stageField(tx, STAGE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, STAGED_RESTRICTION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newVersion).isEqualTo(1L);

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, SEITAN_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(STAGE_TEMPLATE_ID);
		var staged = overlay.get(STAGE_TEMPLATE_ID);
		assertThat(staged.restriction()).isEqualTo(STAGED_RESTRICTION);
		assertThat(staged.equivalence()).isEqualTo(STAGE_MASTER_EQUIVALENCE);
		assertThat(staged.techniqueNotes()).isEqualTo(STAGE_MASTER_TECHNIQUE_NOTES);
		assertThat(staged.version()).isEqualTo(1L);

		var master = factory.withTransaction(tx -> tx.find(SuggestionTemplateEntity.class, STAGE_TEMPLATE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.getRestriction()).isEqualTo(STAGE_MASTER_RESTRICTION);
	}

	@Test
	void stageFieldRejectsAStaleBaseVersionThenAcceptsTheCurrentOne(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageField(tx, STALE_TEMPLATE_ID, SuggestionTemplateField.EQUIVALENCE, FIRST_STAGED_EQUIVALENCE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.stageField(tx, STALE_TEMPLATE_ID, SuggestionTemplateField.EQUIVALENCE, SECOND_STAGED_EQUIVALENCE, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var newVersion = factory
				.withTransaction(tx -> sut.stageField(tx, STALE_TEMPLATE_ID, SuggestionTemplateField.EQUIVALENCE, SECOND_STAGED_EQUIVALENCE, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newVersion).isEqualTo(2L);

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, SEITAN_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay.get(STALE_TEMPLATE_ID).equivalence()).isEqualTo(SECOND_STAGED_EQUIVALENCE);
		assertThat(overlay.get(STALE_TEMPLATE_ID).version()).isEqualTo(2L);
	}

	@Test
	void revertFieldRemovesTheWorkingCopyRowWhenNoOverrideRemains(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var stagedVersion = factory
				.withTransaction(tx -> sut.stageField(tx, REVERT_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, "Avoid entirely", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedVersion).isEqualTo(1L);

		factory.withTransaction(tx -> sut.revertField(tx, REVERT_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, SEAR_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(REVERT_TEMPLATE_ID);

		var master = factory.withTransaction(tx -> tx.find(SuggestionTemplateEntity.class, REVERT_TEMPLATE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.getRestriction()).isEqualTo(REVERT_MASTER_RESTRICTION);
	}

	@Test
	void revertFieldKeepsTheWorkingCopyRowWhenAnotherFieldIsStillOverridden(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageField(tx, KEEP_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, KEEP_STAGED_RESTRICTION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageField(tx, KEEP_TEMPLATE_ID, SuggestionTemplateField.EQUIVALENCE, KEEP_STAGED_EQUIVALENCE, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertField(tx, KEEP_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, 2L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, SEAR_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(KEEP_TEMPLATE_ID);
		var staged = overlay.get(KEEP_TEMPLATE_ID);
		assertThat(staged.restriction()).isEqualTo(KEEP_MASTER_RESTRICTION);
		assertThat(staged.equivalence()).isEqualTo(KEEP_STAGED_EQUIVALENCE);
		assertThat(staged.version()).isEqualTo(3L);
	}

	@Test
	void revertFieldRejectsAStaleBaseVersionLeavingTheStagedRowIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageField(tx, STALE_REVERT_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, STALE_REVERT_STAGED_RESTRICTION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.revertField(tx, STALE_REVERT_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillStaged = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, STALE_REVERT_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillStaged.get(STALE_REVERT_TEMPLATE_ID).restriction()).isEqualTo(STALE_REVERT_STAGED_RESTRICTION);
		assertThat(stillStaged.get(STALE_REVERT_TEMPLATE_ID).version()).isEqualTo(1L);

		factory.withTransaction(tx -> sut.revertField(tx, STALE_REVERT_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var afterRevert = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, STALE_REVERT_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(afterRevert).doesNotContainKey(STALE_REVERT_TEMPLATE_ID);
	}

	@Test
	void findRuleIdsWithStagedTemplatesFlagsARuleOnlyOnceOneOfItsTemplatesIsStaged(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(before).doesNotContain(MINCED_IN_SAUCE_RULE_ID);

		factory.withTransaction(tx -> sut.stageField(tx, FIRST_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, "Staged for the flag", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after).contains(MINCED_IN_SAUCE_RULE_ID);
	}

	private static Uni<Void> createRuleWithoutTemplates(ReactivePersistenceTxContext tx, UUID id) {
		var role = new RoleOrTechniqueEntity();
		role.setId(id);
		role.setName("Role for rule " + id);
		var rule = new RuleEntity();
		rule.setId(id);
		rule.setRecommendation(tx.getReference(RecommendationEntity.class, DECREASE_RED_MEAT_RECOMMENDATION_ID));
		rule.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, BEEF_ID));
		rule.setRoleOrTechnique(role);
		rule.setRationale("A rule with no suggestion templates.");
		return tx.persist(role).chain(() -> tx.persist(rule)).replaceWithVoid();
	}
}
