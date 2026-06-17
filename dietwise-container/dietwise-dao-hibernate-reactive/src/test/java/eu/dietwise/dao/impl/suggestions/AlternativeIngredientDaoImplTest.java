package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Seasonality;
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
class AlternativeIngredientDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("70000000-0000-0000-0000-00000000001f");
	private static final String NEW_ALTERNATIVE_INGREDIENT_NAME = "Aquafaba";
	private static final String OVERLAID_NAME = "Brown lentils (revised)";

	private static final UUID EDIT_UNCHANGED_AI_ID = UUID.fromString("a1c2d3e4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID EDIT_SEED_AI_ID = UUID.fromString("a1c2d3e4-0002-4f5a-8b9c-0d1e2f3a0002");
	private static final UUID EDIT_COLLAPSE_AI_ID = UUID.fromString("a1c2d3e4-0003-4f5a-8b9c-0d1e2f3a0003");
	private static final UUID EDIT_STALE_AI_ID = UUID.fromString("a1c2d3e4-0004-4f5a-8b9c-0d1e2f3a0004");
	private static final String EDIT_UNCHANGED_NAME = "Issue 17 cashew base";
	private static final String EDIT_SEED_NAME = "Issue 17 silken tofu";
	private static final String EDIT_COLLAPSE_NAME = "Issue 17 oat cream";
	private static final String EDIT_STALE_NAME = "Issue 17 coconut yogurt";
	private static final String EDIT_SEED_EDITED_NAME = "Issue 17 silken tofu (edited)";
	private static final String EDIT_COLLAPSE_EDITED_NAME = "Issue 17 oat cream (edited)";
	private static final String EDIT_STALE_EDITED_NAME = "Issue 17 coconut yogurt (edited)";
	private static final String EDITED_EXPLANATION = "Soft soy curd; blends smooth.";

	private static final UUID TRANSLATION_AI_ID = UUID.fromString("a1c2d3e4-0010-4f5a-8b9c-0d1e2f3a0010");
	private static final UUID TRANSLATION_COLLAPSE_AI_ID = UUID.fromString("a1c2d3e4-0011-4f5a-8b9c-0d1e2f3a0011");
	private static final UUID TRANSLATION_BUMP_AI_ID = UUID.fromString("a1c2d3e4-0012-4f5a-8b9c-0d1e2f3a0012");
	private static final UUID TRANSLATION_REVERT_AI_ID = UUID.fromString("a1c2d3e4-0013-4f5a-8b9c-0d1e2f3a0013");
	private static final UUID TRANSLATION_REVERT_STALE_AI_ID = UUID.fromString("a1c2d3e4-0014-4f5a-8b9c-0d1e2f3a0014");
	private static final UUID TRANSLATION_NULL_AI_ID = UUID.fromString("a1c2d3e4-0015-4f5a-8b9c-0d1e2f3a0015");
	private static final UUID REVERT_AI_ID = UUID.fromString("a1c2d3e4-0020-4f5a-8b9c-0d1e2f3a0020");
	private static final UUID REVERT_STALE_AI_ID = UUID.fromString("a1c2d3e4-0021-4f5a-8b9c-0d1e2f3a0021");
	private static final UUID REVERT_NOOP_AI_ID = UUID.fromString("a1c2d3e4-0022-4f5a-8b9c-0d1e2f3a0022");
	private static final String MASTER_EL_NAME = "Κρέμα κάσιους";
	private static final String MASTER_EL_EXPLANATION = "Κρέμα από κάσιους.";
	private static final String EDITED_EL_NAME = "Κρέμα κάσιους (αναθ.)";
	private static final String EDITED_EL_EXPLANATION = "Αναθεωρημένη εξήγηση.";
	private static final String STAGED_NL_NAME = "Cashewroom";
	private static final String STAGED_NL_EXPLANATION = "Romige cashewbasis.";
	private static final String RESTAGED_NL_NAME = "Cashewroom herzien";
	private static final String RESTAGED_NL_EXPLANATION = "Herziene uitleg.";

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
	void testFindAllReturnsSeasonalityByCountry(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx ->
				tx.find(AlternativeIngredientEntity.class, ALTERNATIVE_INGREDIENT_ID)
						.flatMap(alternativeIngredient -> {
							var greece = new AlternativeIngredientSeasonalityEntity();
							greece.setAlternativeIngredient(alternativeIngredient);
							greece.setCountry(GREECE);
							greece.setMonthFrom(8);
							greece.setMonthTo(10);

							var belgium = new AlternativeIngredientSeasonalityEntity();
							belgium.setAlternativeIngredient(alternativeIngredient);
							belgium.setCountry(BELGIUM);
							belgium.setMonthFrom(7);
							belgium.setMonthTo(9);

							alternativeIngredient.setSeasonalityByCountry(Set.of(greece, belgium));
							return tx.persistAll(greece, belgium).replaceWith(alternativeIngredient);
						})
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var alternatives = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.EN)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var alternative = alternatives.stream()
				.filter(a -> a.getId().getId().asUuid().equals(ALTERNATIVE_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(alternative.getSeasonalityByCountry()).isPresent();
		assertThat(alternative.getSeasonalityByCountry().orElseThrow())
				.containsEntry(GREECE, seasonality(8, 10))
				.containsEntry(BELGIUM, seasonality(7, 9));
	}

	@Test
	@Order(1)
	void testFindAllReturnsLocalizedAlternativeIngredientWithFallback(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var alternatives = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var alternative = alternatives.stream()
				.filter(a -> a.getId().getId().asUuid().equals(ALTERNATIVE_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(alternative.getName()).isEqualTo("Bruine linzen (gekookt)");
		assertThat(alternative.getExplanationForLlm()).isEmpty();
	}

	@Test
	@Order(1)
	void listOptionsReturnsPublishedAlternativeIngredientsSortedByName(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var options = factory.withoutTransaction(sut::listOptions).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).contains(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Brown lentils (cooked)"));
		assertThat(options).isSortedAccordingTo(Comparator.comparing(ReferenceOption::name));
	}

	@Test
	@Order(2)
	void createAlternativeIngredientStagesAMirrorRowVisibleInListOptions(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createAlternativeIngredient(tx, NEW_ALTERNATIVE_INGREDIENT_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(options).contains(new ReferenceOption(newId, NEW_ALTERNATIVE_INGREDIENT_NAME));

		var stored = factory.withoutTransaction(em -> em.find(AlternativeIngredientWcEntity.class, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stored.getVersion()).isEqualTo(1L);
		assertThat(stored.getExplanationForLlm()).isNull();
	}

	@Test
	@Order(3)
	void listOptionsOverlaysWorkingCopyOnMasterMirrorWinningById(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var mirror = new AlternativeIngredientWcEntity();
			mirror.setId(ALTERNATIVE_INGREDIENT_ID);
			mirror.setName(OVERLAID_NAME);
			mirror.setVersion(1L);
			return tx.persist(mirror);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).contains(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, OVERLAID_NAME));
		assertThat(options).doesNotContain(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Brown lentils (cooked)"));
		assertThat(options.stream().filter(option -> option.id().equals(ALTERNATIVE_INGREDIENT_ID))).hasSize(1);
		assertThat(options).isSortedAccordingTo(Comparator.comparing(ReferenceOption::name));
	}

	@Test
	@Order(4)
	void findEditableByIdReturnsMasterDetailsWhenUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, EDIT_UNCHANGED_AI_ID, EDIT_UNCHANGED_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, EDIT_UNCHANGED_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(details.name()).isEqualTo(EDIT_UNCHANGED_NAME);
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
	}

	@Test
	@Order(5)
	void editAlternativeIngredientSeedsAMirrorRowOnFirstTouch(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, EDIT_SEED_AI_ID, EDIT_SEED_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, EDIT_SEED_AI_ID, EDIT_SEED_EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, EDIT_SEED_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDIT_SEED_EDITED_NAME);
		assertThat(details.explanationForLlm()).isEqualTo(EDITED_EXPLANATION);
		assertThat(details.version()).isEqualTo(1L);
		assertThat(details.published()).isTrue();

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).containsEntry(EDIT_SEED_AI_ID, EDIT_SEED_EDITED_NAME);
	}

	@Test
	@Order(6)
	void editAlternativeIngredientBackToMasterValuesCollapsesTheMirrorRow(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, EDIT_COLLAPSE_AI_ID, EDIT_COLLAPSE_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, EDIT_COLLAPSE_AI_ID, EDIT_COLLAPSE_EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, EDIT_COLLAPSE_AI_ID, EDIT_COLLAPSE_NAME, null, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(EDIT_COLLAPSE_AI_ID);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, EDIT_COLLAPSE_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDIT_COLLAPSE_NAME);
		assertThat(details.version()).isZero();
	}

	@Test
	@Order(7)
	void editAlternativeIngredientRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, EDIT_STALE_AI_ID, EDIT_STALE_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, EDIT_STALE_AI_ID, EDIT_STALE_EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, EDIT_STALE_AI_ID, "Issue 17 stale name", "stale", 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, EDIT_STALE_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDIT_STALE_EDITED_NAME);
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(8)
	void editWorkingCopyOnlyAlternativeIngredientBumpsItsMirror(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createAlternativeIngredient(tx, "Soy curls"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, newId, "Soy curls (strips)", "Rehydrated textured soy.", 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Soy curls (strips)");
		assertThat(details.explanationForLlm()).isEqualTo("Rehydrated textured soy.");
		assertThat(details.version()).isEqualTo(2L);
		assertThat(details.published()).isFalse();
	}

	@Test
	@Order(9)
	void findEditableByIdRejectsUnknownAlternativeIngredient(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		assertThatThrownBy(() -> factory.withoutTransaction(em -> sut.findEditableById(em, UUID.randomUUID()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);
	}

	@Test
	@Order(10)
	void findTranslationsForEditOverlaysStagedOnMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, TRANSLATION_AI_ID, "Cashew cream (translation test)")
						.chain(() -> persistAlternativeIngredientTranslation(tx, TRANSLATION_AI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_AI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L, false));
		assertThat(forEdit.get(RecipeLanguage.LT)).isEqualTo(new ReferenceDetails(null, null, 0L, false));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_AI_ID).present()).containsExactly(RecipeLanguage.EL);
		assertThat(langs.get(TRANSLATION_AI_ID).staged()).containsExactly(RecipeLanguage.NL);
	}

	@Test
	@Order(11)
	void stageTranslationSeedsThenCollapsesToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, TRANSLATION_COLLAPSE_AI_ID, "Oat cream (translation test)")
						.chain(() -> persistAlternativeIngredientTranslation(tx, TRANSLATION_COLLAPSE_AI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_AI_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged.get(TRANSLATION_COLLAPSE_AI_ID).staged()).containsExactly(RecipeLanguage.EL);

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_AI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_COLLAPSE_AI_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_COLLAPSE_AI_ID).present()).containsExactly(RecipeLanguage.EL);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_COLLAPSE_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
	}

	@Test
	@Order(12)
	void stageTranslationBumpsThenRejectsStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, TRANSLATION_BUMP_AI_ID, "Coconut yogurt (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_AI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_AI_ID, RecipeLanguage.NL, RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_BUMP_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 2L, false));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_AI_ID, RecipeLanguage.NL, "Stale.", "x", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	@Order(13)
	void revertTranslationRemovesStagedRowRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, TRANSLATION_REVERT_AI_ID, "Seitan cream (translation test)")
						.chain(() -> persistAlternativeIngredientTranslation(tx, TRANSLATION_REVERT_AI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_AI_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_AI_ID, RecipeLanguage.EL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_REVERT_AI_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_REVERT_AI_ID).present()).containsExactly(RecipeLanguage.EL);
	}

	@Test
	@Order(14)
	void revertTranslationRejectsStaleBaseVersionLeavingItIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, TRANSLATION_REVERT_STALE_AI_ID, "Edamame cream (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_STALE_AI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_AI_ID, RecipeLanguage.NL, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillStaged = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillStaged.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L, false));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_AI_ID, RecipeLanguage.NL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var afterRevert = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(afterRevert.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(null, null, 0L, false));
	}

	@Test
	@Order(15)
	void stageNullTranslationWithNoMasterIsANoOp(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, TRANSLATION_NULL_AI_ID, "Quinoa cream (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_NULL_AI_ID, RecipeLanguage.EL, null, null, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs).doesNotContainKey(TRANSLATION_NULL_AI_ID);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_NULL_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(null, null, 0L, false));
	}

	@Test
	@Order(16)
	void revertAlternativeIngredientRemovesStagedEditRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, REVERT_AI_ID, "Black bean cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, REVERT_AI_ID, "Black bean cream (edited)", "Legume base.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertAlternativeIngredient(tx, REVERT_AI_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Black bean cream");
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(REVERT_AI_ID);
	}

	@Test
	@Order(17)
	void revertAlternativeIngredientRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, REVERT_STALE_AI_ID, "Pinto bean cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editAlternativeIngredient(tx, REVERT_STALE_AI_ID, "Pinto bean cream (edited)", "Legume base.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertAlternativeIngredient(tx, REVERT_STALE_AI_ID, 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_STALE_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Pinto bean cream (edited)");
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(18)
	void revertAlternativeIngredientIsANoOpWhenThereIsNoStagedEdit(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, REVERT_NOOP_AI_ID, "Kidney bean cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertAlternativeIngredient(tx, REVERT_NOOP_AI_ID, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_NOOP_AI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Kidney bean cream");
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
	}

	@Test
	@Order(19)
	void revertWorkingCopyOnlyAlternativeIngredientIsRefused(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createAlternativeIngredient(tx, "Mung bean cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertAlternativeIngredient(tx, newId, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Mung bean cream");
		assertThat(details.version()).isEqualTo(1L);
		assertThat(details.published()).isFalse();
	}

	private static Uni<Void> persistAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id, String name) {
		var entity = new AlternativeIngredientEntity();
		entity.setId(id);
		entity.setName(name);
		return tx.persist(entity).replaceWithVoid();
	}

	private static Uni<Void> persistAlternativeIngredientTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm) {
		var translation = new AlternativeIngredientTranslationEntity();
		translation.setAlternativeIngredient(tx.getReference(AlternativeIngredientEntity.class, id));
		translation.setLang(lang);
		translation.setName(name);
		translation.setExplanationForLlm(explanationForLlm);
		return tx.persist(translation).replaceWithVoid();
	}

	private static Seasonality seasonality(int monthFrom, int monthTo) {
		return eu.dietwise.v1.types.ImmutableSeasonality.builder().monthFrom(monthFrom).monthTo(monthTo).build();
	}
}
