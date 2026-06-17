package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationEntityId;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationWcEntity;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateTranslationWcEntityId;
import eu.dietwise.dao.jpa.suggestions.SuggestionTemplateWcEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
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

	// Seeded templates used for staging translations; each test uses a distinct template so the shared Working Copy stays
	// isolated. Every one has EL/LT/NL master translations of restriction and equivalence, but no technique_notes.
	private static final UUID TRANSLATE_TEMPLATE_ID = UUID.fromString("943d545b-5880-4da7-820f-8d64b97a3ad9");
	private static final UUID STALE_TRANSLATE_TEMPLATE_ID = UUID.fromString("c535d78d-3cce-4c7c-9eda-7f19b30ca461");
	private static final UUID REVERT_TRANSLATE_TEMPLATE_ID = UUID.fromString("e781bf4e-dbfb-4f42-a7e7-0cd65ebcff28");
	private static final UUID KEEP_TRANSLATE_TEMPLATE_ID = UUID.fromString("5e0b5d4b-6c7d-462d-b380-c299fb3fb2ff");
	private static final UUID STALE_REVERT_TRANSLATE_TEMPLATE_ID = UUID.fromString("5446a40f-b0f7-4942-99d5-da7fe9f632eb");

	// Restriction and equivalence translated in EL/LT/NL; technique_notes translated in none.
	private static final UUID CHIP_RULE_ID = UUID.fromString("95fcf98d-fc75-4773-9034-22ad58584a1a");
	private static final UUID CHIP_TEMPLATE_ID = UUID.fromString("2b7fe2ce-0ad3-4999-b3ba-618bd10e56fc");

	private static final UUID TRANSLATION_FLAG_RULE_ID = UUID.fromString("a256697c-5499-4092-a396-9f92846c5a96");
	private static final UUID TRANSLATION_FLAG_TEMPLATE_ID = UUID.fromString("12e06b1e-ef4e-4faf-ac3f-6b7a475e9798");

	// Seeded active templates used for Deactivate/Activate staging; each test uses a distinct template so the shared
	// Working Copy stays isolated. All belong to TOGGLE_RULE_ID and are published active.
	private static final UUID TOGGLE_RULE_ID = UUID.fromString("14000000-0000-4000-8000-000000000001");
	private static final UUID DEACTIVATE_TEMPLATE_ID = UUID.fromString("14000000-0000-4000-8000-0000000000d1");
	private static final UUID REACTIVATE_TEMPLATE_ID = UUID.fromString("14000000-0000-4000-8000-0000000000d2");
	private static final UUID KEEP_ACTIVE_TEMPLATE_ID = UUID.fromString("14000000-0000-4000-8000-0000000000d3");
	private static final String KEEP_ACTIVE_MASTER_RESTRICTION = "Keep-override fixture";
	// Seeded AlternativeIngredients reused as the alternatives of the toggle fixtures' templates.
	private static final UUID FIRST_ALTERNATIVE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
	private static final UUID SECOND_ALTERNATIVE_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
	private static final UUID THIRD_ALTERNATIVE_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");
	private static final String SMOKED_TOFU = "Smoked tofu cubes";

	// Seeded rules used for adding and discarding Working-Copy-only templates; each test uses its own rule so the shared
	// Working Copy stays isolated. ADD_RULE and DEDUP_RULE start with published master templates; the others start empty.
	private static final UUID ADD_RULE_ID = UUID.fromString("15000000-0000-4000-8000-000000000001");
	private static final UUID ADD_TEMPLATE_A_ID = UUID.fromString("15000000-0000-4000-8000-0000000000a1");
	private static final UUID ADD_TEMPLATE_B_ID = UUID.fromString("15000000-0000-4000-8000-0000000000a2");
	private static final UUID DEDUP_RULE_ID = UUID.fromString("15000000-0000-4000-8000-000000000002");
	private static final UUID DEDUP_TEMPLATE_ID = UUID.fromString("15000000-0000-4000-8000-0000000000b1");
	private static final UUID WC_ONLY_RULE_ID = UUID.fromString("15000000-0000-4000-8000-000000000003");
	private static final UUID DISCARD_RULE_ID = UUID.fromString("15000000-0000-4000-8000-000000000004");
	private static final UUID DISCARD_STALE_RULE_ID = UUID.fromString("15000000-0000-4000-8000-000000000005");
	private static final UUID REFUSE_RULE_ID = UUID.fromString("15000000-0000-4000-8000-000000000006");
	private static final UUID REFUSE_TEMPLATE_ID = UUID.fromString("15000000-0000-4000-8000-0000000000c1");

	// Issue 17: dedicated AlternativeIngredients referenced only by Issue-17 fixtures, so the blast-radius count and the
	// shared-AlternativeIngredient flag stay deterministic regardless of test order.
	private static final UUID BLAST_ALTERNATIVE_ID = UUID.fromString("17000000-0000-4000-8000-0000000000a1");
	private static final UUID FLAG_ALTERNATIVE_ID = UUID.fromString("17000000-0000-4000-8000-0000000000a2");
	private static final UUID FLAG_TR_ALTERNATIVE_ID = UUID.fromString("17000000-0000-4000-8000-0000000000a3");
	private static final UUID ALT_IDS_RULE_ID = UUID.fromString("17000000-0000-4000-8000-000000000001");
	private static final UUID ALT_IDS_TEMPLATE_A_ID = UUID.fromString("17000000-0000-4000-8000-0000000000b1");
	private static final UUID ALT_IDS_TEMPLATE_B_ID = UUID.fromString("17000000-0000-4000-8000-0000000000b2");
	private static final UUID BLAST_RULE_ID = UUID.fromString("17000000-0000-4000-8000-000000000002");
	private static final UUID BLAST_TEMPLATE_ID = UUID.fromString("17000000-0000-4000-8000-0000000000b3");
	private static final UUID BLAST_WC_RULE_ID = UUID.fromString("17000000-0000-4000-8000-000000000003");
	private static final UUID ALT_FLAG_RULE_ID = UUID.fromString("17000000-0000-4000-8000-000000000004");
	private static final UUID ALT_FLAG_TEMPLATE_ID = UUID.fromString("17000000-0000-4000-8000-0000000000b4");
	private static final UUID ALT_FLAG_TR_RULE_ID = UUID.fromString("17000000-0000-4000-8000-000000000005");
	private static final UUID ALT_FLAG_TR_TEMPLATE_ID = UUID.fromString("17000000-0000-4000-8000-0000000000b5");
	private static final UUID NEW_ALT_RULE_ID = UUID.fromString("17000000-0000-4000-8000-000000000006");
	private static final String NEW_ALT_NAME = "Issue 17 brand-new alternative";

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

	@BeforeAll
	static void seedDeactivateActivateFixtures(Mutiny.SessionFactory sessionFactory) {
		sessionFactory.withTransaction(session -> SuggestionTemplateFixtures.insertRuleWithTemplates(
						session, TOGGLE_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 14 toggle role",
						List.of(
								new SuggestionTemplateFixtures.Template(DEACTIVATE_TEMPLATE_ID, FIRST_ALTERNATIVE_ID, 0, "Deactivate fixture", true),
								new SuggestionTemplateFixtures.Template(REACTIVATE_TEMPLATE_ID, SECOND_ALTERNATIVE_ID, 1, "Reactivate fixture", true),
								new SuggestionTemplateFixtures.Template(KEEP_ACTIVE_TEMPLATE_ID, THIRD_ALTERNATIVE_ID, 2, KEEP_ACTIVE_MASTER_RESTRICTION, true)
						)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@BeforeAll
	static void seedAddDiscardFixtures(Mutiny.SessionFactory sessionFactory) {
		sessionFactory.withTransaction(session -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, ADD_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 15 add role",
								List.of(
										new SuggestionTemplateFixtures.Template(ADD_TEMPLATE_A_ID, FIRST_ALTERNATIVE_ID, 0, "Add fixture A", true),
										new SuggestionTemplateFixtures.Template(ADD_TEMPLATE_B_ID, SECOND_ALTERNATIVE_ID, 1, "Add fixture B", true)))
						.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, DEDUP_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 15 dedup role",
								List.of(new SuggestionTemplateFixtures.Template(DEDUP_TEMPLATE_ID, FIRST_ALTERNATIVE_ID, 0, "Dedup fixture", true))))
						.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, WC_ONLY_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 15 wc-only role", List.of()))
						.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, DISCARD_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 15 discard role", List.of()))
						.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, DISCARD_STALE_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 15 discard-stale role", List.of()))
						.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, REFUSE_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 15 refuse role",
								List.of(new SuggestionTemplateFixtures.Template(REFUSE_TEMPLATE_ID, FIRST_ALTERNATIVE_ID, 0, "Refuse fixture", true)))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@BeforeAll
	static void seedAlternativeIngredientEditFixtures(Mutiny.SessionFactory sessionFactory) {
		sessionFactory.withTransaction(session ->
						insertAlternativeIngredient(session, BLAST_ALTERNATIVE_ID, "Issue 17 blast tofu")
								.chain(() -> insertAlternativeIngredient(session, FLAG_ALTERNATIVE_ID, "Issue 17 flag tofu"))
								.chain(() -> insertAlternativeIngredient(session, FLAG_TR_ALTERNATIVE_ID, "Issue 17 flag-tr tempeh"))
								.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
										session, ALT_IDS_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 17 alt-ids role",
										List.of(
												new SuggestionTemplateFixtures.Template(ALT_IDS_TEMPLATE_A_ID, FIRST_ALTERNATIVE_ID, 0, "Alt-ids fixture A", true),
												new SuggestionTemplateFixtures.Template(ALT_IDS_TEMPLATE_B_ID, BLAST_ALTERNATIVE_ID, 1, "Alt-ids fixture B", true))))
								.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
										session, BLAST_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 17 blast role",
										List.of(new SuggestionTemplateFixtures.Template(BLAST_TEMPLATE_ID, BLAST_ALTERNATIVE_ID, 0, "Blast fixture", true))))
								.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
										session, BLAST_WC_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 17 blast-wc role", List.of()))
								.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
										session, ALT_FLAG_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 17 flag role",
										List.of(new SuggestionTemplateFixtures.Template(ALT_FLAG_TEMPLATE_ID, FLAG_ALTERNATIVE_ID, 0, "Flag fixture", true))))
								.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
										session, ALT_FLAG_TR_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 17 flag-tr role",
										List.of(new SuggestionTemplateFixtures.Template(ALT_FLAG_TR_TEMPLATE_ID, FLAG_TR_ALTERNATIVE_ID, 0, "Flag-tr fixture", true)))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@Test
	void findAlternativeIdsByRuleMapsEachTemplateToItsAlternative(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var ids = factory.withoutTransaction(em -> sut.findAlternativeIdsByRule(em, ALT_IDS_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(ids).hasSize(2);
		assertThat(ids).containsEntry(ALT_IDS_TEMPLATE_A_ID, FIRST_ALTERNATIVE_ID);
		assertThat(ids).containsEntry(ALT_IDS_TEMPLATE_B_ID, BLAST_ALTERNATIVE_ID);
	}

	@Test
	void countTemplatesByAlternativeCountsMasterAndWorkingCopyOnlyTemplates(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory.withoutTransaction(em -> sut.countTemplatesByAlternative(em, BLAST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(before).isEqualTo(2L);

		factory.withTransaction(tx -> sut.addTemplate(tx, BLAST_WC_RULE_ID, BLAST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(em -> sut.countTemplatesByAlternative(em, BLAST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after).isEqualTo(3L);
	}

	@Test
	void findRuleIdsWithStagedTemplatesFlagsARuleWhoseAlternativeHasAStagedEdit(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(before).doesNotContain(ALT_FLAG_RULE_ID);

		factory.withTransaction(tx -> {
			var wc = new AlternativeIngredientWcEntity();
			wc.setId(FLAG_ALTERNATIVE_ID);
			wc.setName("Issue 17 flag tofu (edited)");
			wc.setVersion(1L);
			return tx.persist(wc);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after).contains(ALT_FLAG_RULE_ID);
	}

	@Test
	void findRuleIdsWithStagedTemplatesFlagsARuleWhoseAlternativeHasAStagedTranslation(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(before).doesNotContain(ALT_FLAG_TR_RULE_ID);

		factory.withTransaction(tx -> {
			var wc = new AlternativeIngredientTranslationWcEntity();
			wc.setAlternativeIngredientId(FLAG_TR_ALTERNATIVE_ID);
			wc.setLang(RecipeLanguage.EL);
			wc.setName("Σταγμένη μετάφραση");
			wc.setVersion(1L);
			return tx.persist(wc);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after).contains(ALT_FLAG_TR_RULE_ID);
	}

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

	@Test
	void stageFieldTranslationStoresTheEditInTheWorkingCopyLeavingMasterUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory
				.withoutTransaction(em -> sut.findFieldTranslationsForEdit(em, TRANSLATE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		String masterRestrictionEl = before.get(RecipeLanguage.EL).text();
		assertThat(before.get(RecipeLanguage.EL).version()).isEqualTo(0L);

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, "Greek restriction override", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory
				.withoutTransaction(em -> sut.findFieldTranslationsForEdit(em, TRANSLATE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after.get(RecipeLanguage.EL).text()).isEqualTo("Greek restriction override");
		assertThat(after.get(RecipeLanguage.EL).version()).isEqualTo(1L);

		var master = factory.withTransaction(tx -> tx.find(SuggestionTemplateTranslationEntity.class, new SuggestionTemplateTranslationEntityId(TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.getRestriction()).isEqualTo(masterRestrictionEl);

		var wc = factory.withTransaction(tx -> tx.find(SuggestionTemplateTranslationWcEntity.class, new SuggestionTemplateTranslationWcEntityId(TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(wc.getRestriction()).isEqualTo("Greek restriction override");
		assertThat(wc.getEquivalence()).isEqualTo(master.getEquivalence());
	}

	@Test
	void stageFieldTranslationRejectsAStaleBaseVersionThenAcceptsTheCurrentOne(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, STALE_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.EQUIVALENCE, "First Greek equivalence", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.stageFieldTranslation(tx, STALE_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.EQUIVALENCE, "Second Greek equivalence", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, STALE_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.EQUIVALENCE, "Second Greek equivalence", 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory
				.withoutTransaction(em -> sut.findFieldTranslationsForEdit(em, STALE_TRANSLATE_TEMPLATE_ID, SuggestionTemplateField.EQUIVALENCE))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL).text()).isEqualTo("Second Greek equivalence");
		assertThat(forEdit.get(RecipeLanguage.EL).version()).isEqualTo(2L);
	}

	@Test
	void revertFieldTranslationRemovesTheWorkingCopyRowWhenNoOverrideRemains(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var master = factory
				.withoutTransaction(em -> sut.findFieldTranslationsForEdit(em, REVERT_TRANSLATE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		String masterRestrictionEl = master.get(RecipeLanguage.EL).text();

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, REVERT_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, "Temporary Greek override", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertFieldTranslation(tx, REVERT_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var wc = factory.withTransaction(tx -> tx.find(SuggestionTemplateTranslationWcEntity.class, new SuggestionTemplateTranslationWcEntityId(REVERT_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(wc).isNull();

		var after = factory
				.withoutTransaction(em -> sut.findFieldTranslationsForEdit(em, REVERT_TRANSLATE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after.get(RecipeLanguage.EL).text()).isEqualTo(masterRestrictionEl);
		assertThat(after.get(RecipeLanguage.EL).version()).isEqualTo(0L);
	}

	@Test
	void revertFieldTranslationKeepsTheWorkingCopyRowWhenAnotherFieldIsStillOverridden(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var master = factory
				.withoutTransaction(em -> sut.findFieldTranslationsForEdit(em, KEEP_TRANSLATE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		String masterRestrictionEl = master.get(RecipeLanguage.EL).text();

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, KEEP_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, "Greek restriction override", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, KEEP_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.EQUIVALENCE, "Greek equivalence override", 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertFieldTranslation(tx, KEEP_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, 2L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var wc = factory.withTransaction(tx -> tx.find(SuggestionTemplateTranslationWcEntity.class, new SuggestionTemplateTranslationWcEntityId(KEEP_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(wc).isNotNull();
		assertThat(wc.getRestriction()).isEqualTo(masterRestrictionEl);
		assertThat(wc.getEquivalence()).isEqualTo("Greek equivalence override");
		assertThat(wc.getVersion()).isEqualTo(3L);
	}

	@Test
	void revertFieldTranslationRejectsAStaleBaseVersionLeavingTheStagedRowIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, STALE_REVERT_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, "Greek override to keep", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.revertFieldTranslation(tx, STALE_REVERT_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var wc = factory.withTransaction(tx -> tx.find(SuggestionTemplateTranslationWcEntity.class, new SuggestionTemplateTranslationWcEntityId(STALE_REVERT_TRANSLATE_TEMPLATE_ID, RecipeLanguage.EL)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(wc).isNotNull();
		assertThat(wc.getRestriction()).isEqualTo("Greek override to keep");
		assertThat(wc.getVersion()).isEqualTo(1L);
	}

	@Test
	void findFieldTranslationLangsByRuleReportsPresentStagedAndMissingPerField(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory.withoutTransaction(em -> sut.findFieldTranslationLangsByRule(em, CHIP_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var chips = before.get(CHIP_TEMPLATE_ID);
		assertThat(chips.restriction().present()).containsExactlyInAnyOrder(RecipeLanguage.EL, RecipeLanguage.LT, RecipeLanguage.NL);
		assertThat(chips.restriction().staged()).isEmpty();
		assertThat(chips.equivalence().present()).containsExactlyInAnyOrder(RecipeLanguage.EL, RecipeLanguage.LT, RecipeLanguage.NL);
		assertThat(chips.techniqueNotes().present()).isEmpty();
		assertThat(chips.techniqueNotes().staged()).isEmpty();

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, CHIP_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.TECHNIQUE_NOTES, "Greek technique notes", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(em -> sut.findFieldTranslationLangsByRule(em, CHIP_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var afterChips = after.get(CHIP_TEMPLATE_ID);
		assertThat(afterChips.techniqueNotes().staged()).containsExactly(RecipeLanguage.EL);
		assertThat(afterChips.techniqueNotes().present()).isEmpty();
		assertThat(afterChips.restriction().present()).containsExactlyInAnyOrder(RecipeLanguage.EL, RecipeLanguage.LT, RecipeLanguage.NL);
		assertThat(afterChips.restriction().staged()).isEmpty();
	}

	@Test
	void findRuleIdsWithStagedTemplatesAlsoFlagsARuleWithAStagedTranslation(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var before = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(before).doesNotContain(TRANSLATION_FLAG_RULE_ID);

		factory.withTransaction(tx -> sut.stageFieldTranslation(tx, TRANSLATION_FLAG_TEMPLATE_ID, RecipeLanguage.EL, SuggestionTemplateField.RESTRICTION, "Staged translation for the flag", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var after = factory.withoutTransaction(sut::findRuleIdsWithStagedTemplates)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(after).contains(TRANSLATION_FLAG_RULE_ID);
	}

	@Test
	void setActiveStagesDeactivationInTheWorkingCopyLeavingMasterActive(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.setActive(tx, DEACTIVATE_TEMPLATE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, TOGGLE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(DEACTIVATE_TEMPLATE_ID);
		var staged = overlay.get(DEACTIVATE_TEMPLATE_ID);
		assertThat(staged.active()).isFalse();
		assertThat(staged.version()).isEqualTo(1L);

		var master = factory.withTransaction(tx -> tx.find(SuggestionTemplateEntity.class, DEACTIVATE_TEMPLATE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.isActive()).isTrue();
	}

	@Test
	void setActiveReactivatingToMasterCollapsesTheWorkingCopyRowAndRejectsAStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.setActive(tx, REACTIVATE_TEMPLATE_ID, false, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.setActive(tx, REACTIVATE_TEMPLATE_ID, true, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		factory.withTransaction(tx -> sut.setActive(tx, REACTIVATE_TEMPLATE_ID, true, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, TOGGLE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).doesNotContainKey(REACTIVATE_TEMPLATE_ID);

		var master = factory.withTransaction(tx -> tx.find(SuggestionTemplateEntity.class, REACTIVATE_TEMPLATE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master.isActive()).isTrue();
	}

	@Test
	void revertFieldKeepsTheWorkingCopyRowWhenTheTemplateIsStillDeactivated(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageField(tx, KEEP_ACTIVE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, "Temporary override", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.setActive(tx, KEEP_ACTIVE_TEMPLATE_ID, false, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertField(tx, KEEP_ACTIVE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, 2L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, TOGGLE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(KEEP_ACTIVE_TEMPLATE_ID);
		var staged = overlay.get(KEEP_ACTIVE_TEMPLATE_ID);
		assertThat(staged.restriction()).isEqualTo(KEEP_ACTIVE_MASTER_RESTRICTION);
		assertThat(staged.active()).isFalse();
		assertThat(staged.version()).isEqualTo(3L);
	}

	@Test
	void addTemplateCreatesAWorkingCopyOnlyTemplatePositionedAfterExistingTemplates(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.addTemplate(tx, ADD_RULE_ID, THIRD_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newTemplates = factory.withoutTransaction(em -> sut.findNewByRule(em, ADD_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newTemplates).hasSize(1);
		var added = newTemplates.getFirst();
		assertThat(added.template().getId().asString()).isEqualTo(newId.toString());
		assertThat(added.template().getAlternative().asString()).isEqualTo(SMOKED_TOFU);
		assertThat(added.template().getRestriction()).isEmpty();
		assertThat(added.version()).isEqualTo(1L);

		var master = factory.withoutTransaction(em -> sut.findByRule(em, ADD_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master).hasSize(2);

		var wc = factory.withTransaction(tx -> tx.find(SuggestionTemplateWcEntity.class, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(wc.getAlternativeOrder()).isEqualTo(2);
		assertThat(wc.isActive()).isTrue();
	}

	@Test
	void findNewByRuleResolvesTheNameOfABrandNewWorkingCopyOnlyAlternative(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var alternativeDao = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newTemplateId = factory.withTransaction(tx -> alternativeDao.createAlternativeIngredient(tx, NEW_ALT_NAME)
						.flatMap(altId -> sut.addTemplate(tx, NEW_ALT_RULE_ID, altId)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newTemplates = factory.withoutTransaction(em -> sut.findNewByRule(em, NEW_ALT_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(newTemplates).hasSize(1);
		var added = newTemplates.getFirst();
		assertThat(added.template().getId().asString()).isEqualTo(newTemplateId.toString());
		assertThat(added.template().getAlternative().asString()).isEqualTo(NEW_ALT_NAME);
	}

	@Test
	void findTemplateIdByRuleAndAlternativeReturnsAnExistingMasterTemplateOrEmpty(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var existing = factory.withoutTransaction(em -> sut.findTemplateIdByRuleAndAlternative(em, DEDUP_RULE_ID, FIRST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(existing).contains(DEDUP_TEMPLATE_ID);

		var none = factory.withoutTransaction(em -> sut.findTemplateIdByRuleAndAlternative(em, DEDUP_RULE_ID, SECOND_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(none).isEmpty();
	}

	@Test
	void findTemplateIdByRuleAndAlternativeFindsAWorkingCopyOnlyTemplateOrderedFromZero(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.addTemplate(tx, WC_ONLY_RULE_ID, FIRST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var found = factory.withoutTransaction(em -> sut.findTemplateIdByRuleAndAlternative(em, WC_ONLY_RULE_ID, FIRST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(found).contains(newId);

		var wc = factory.withTransaction(tx -> tx.find(SuggestionTemplateWcEntity.class, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(wc.getAlternativeOrder()).isZero();
	}

	@Test
	void discardTemplateRemovesAWorkingCopyOnlyTemplate(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.addTemplate(tx, DISCARD_RULE_ID, FIRST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.discardTemplate(tx, newId, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var newTemplates = factory.withoutTransaction(em -> sut.findNewByRule(em, DISCARD_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newTemplates).isEmpty();
	}

	@Test
	void discardTemplateRejectsAStaleBaseVersionLeavingTheTemplate(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.addTemplate(tx, DISCARD_STALE_RULE_ID, FIRST_ALTERNATIVE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.findTemplateIdByRuleAndAlternative(tx, DISCARD_STALE_RULE_ID, FIRST_ALTERNATIVE_ID)
						.flatMap(id -> sut.discardTemplate(tx, id.orElseThrow(), 0L)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var newTemplates = factory.withoutTransaction(em -> sut.findNewByRule(em, DISCARD_STALE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(newTemplates).hasSize(1);
	}

	@Test
	void discardTemplateRefusesAPublishedTemplateKeepingItsStagedEdit(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.stageField(tx, REFUSE_TEMPLATE_ID, SuggestionTemplateField.RESTRICTION, "Staged edit", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory
				.withTransaction(tx -> sut.discardTemplate(tx, REFUSE_TEMPLATE_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);

		var overlay = factory.withoutTransaction(em -> sut.findStagedOverlayByRule(em, REFUSE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(overlay).containsKey(REFUSE_TEMPLATE_ID);

		var master = factory.withTransaction(tx -> tx.find(SuggestionTemplateEntity.class, REFUSE_TEMPLATE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(master).isNotNull();
	}

	private static Uni<Void> insertAlternativeIngredient(Mutiny.Session session, UUID id, String name) {
		return session.createNativeQuery("insert into DW_ALTERNATIVE_INGREDIENT (id, name) values (:id, :name)")
				.setParameter("id", id)
				.setParameter("name", name)
				.executeUpdate()
				.replaceWithVoid();
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
