package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import jakarta.persistence.criteria.CriteriaDelete;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity_;
import eu.dietwise.v1.types.RecipeLanguage;
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
class TriggerIngredientDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	private static final String NEW_TRIGGER_INGREDIENT_NAME = "Quinoa flour";
	private static final String MASTER_NAME = "Beef";
	private static final String EDITED_NAME = "Bovine";
	private static final String EDITED_EXPLANATION = "Red meat; the centrepiece protein.";

	private static final UUID TRANSLATION_TI_ID = UUID.fromString("b1c2d3e4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID TRANSLATION_COLLAPSE_TI_ID = UUID.fromString("b1c2d3e4-0002-4f5a-8b9c-0d1e2f3a0002");
	private static final UUID TRANSLATION_BUMP_TI_ID = UUID.fromString("b1c2d3e4-0003-4f5a-8b9c-0d1e2f3a0003");
	private static final UUID TRANSLATION_REVERT_TI_ID = UUID.fromString("b1c2d3e4-0004-4f5a-8b9c-0d1e2f3a0004");
	private static final UUID TRANSLATION_REVERT_STALE_TI_ID = UUID.fromString("b1c2d3e4-0005-4f5a-8b9c-0d1e2f3a0005");
	private static final UUID TRANSLATION_NULL_TI_ID = UUID.fromString("b1c2d3e4-0006-4f5a-8b9c-0d1e2f3a0006");
	private static final String MASTER_EL_NAME = "Φακές";
	private static final String MASTER_EL_EXPLANATION = "Όσπριο πλούσιο σε πρωτεΐνη.";
	private static final String EDITED_EL_NAME = "Φακές (αναθ.)";
	private static final String EDITED_EL_EXPLANATION = "Αναθεωρημένη εξήγηση.";
	private static final String STAGED_NL_NAME = "Linzen";
	private static final String STAGED_NL_EXPLANATION = "Eiwitrijke peulvrucht.";
	private static final String RESTAGED_NL_NAME = "Linzen herzien";
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
	void testFindAllReturnsBaseTriggerIngredientWhenTranslationIsMissing(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var cb = tx.getCriteriaBuilder();
			CriteriaDelete<TriggerIngredientTranslationEntity> delete = cb.createCriteriaDelete(TriggerIngredientTranslationEntity.class);
			var root = delete.from(TriggerIngredientTranslationEntity.class);
			delete.where(cb.and(
					cb.equal(root.get(TriggerIngredientTranslationEntity_.lang), "NL"),
					cb.equal(root.get(TriggerIngredientTranslationEntity_.name), "Rundvlees")
			));
			return tx.createDelete(delete).execute();
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var triggerIngredient = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(triggerIngredient.getName()).isEqualTo("Beef");
		assertThat(triggerIngredient.getExplanationForLlm()).isEmpty();
	}

	@Test
	@Order(2)
	void testFindAllReturnsLocalizedTriggerIngredientWhenTranslationExists(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> tx.find(TriggerIngredientEntity.class, TRIGGER_INGREDIENT_ID)
						.flatMap(triggerIngredient -> {
							var translation = new TriggerIngredientTranslationEntity();
							translation.setTriggerIngredient(triggerIngredient);
							translation.setLang(RecipeLanguage.NL);
							translation.setName("Rundvlees");
							translation.setExplanationForLlm("NL trigger");
							return tx.persist(translation);
						}))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var triggerIngredient = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(triggerIngredient.getName()).isEqualTo("Rundvlees");
		assertThat(triggerIngredient.getExplanationForLlm()).contains("NL trigger");
	}

	@Test
	@Order(3)
	void testListOptionsReturnsMasterEntries(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var options = factory.withoutTransaction(sut::listOptions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).contains(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"));
	}

	@Test
	@Order(4)
	void testCreateTriggerIngredientStagesAMirrorRowVisibleInListOptions(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, NEW_TRIGGER_INGREDIENT_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(options).contains(new ReferenceOption(newId, NEW_TRIGGER_INGREDIENT_NAME));
		assertThat(options).contains(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"));
	}

	@Test
	@Order(5)
	void testFindEditableByIdReturnsMasterDetailsWhenUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(details.name()).isEqualTo(MASTER_NAME);
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
	}

	@Test
	@Order(6)
	void testEditTriggerIngredientSeedsAMirrorRowOnFirstTouch(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDITED_NAME);
		assertThat(details.explanationForLlm()).isEqualTo(EDITED_EXPLANATION);
		assertThat(details.version()).isEqualTo(1L);

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).containsEntry(TRIGGER_INGREDIENT_ID, EDITED_NAME);
	}

	@Test
	@Order(7)
	void testEditTriggerIngredientBackToMasterValuesCollapsesTheMirrorRow(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, MASTER_NAME, null, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(TRIGGER_INGREDIENT_ID);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(MASTER_NAME);
		assertThat(details.version()).isZero();
	}

	@Test
	@Order(8)
	void testEditTriggerIngredientRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.editTriggerIngredient(tx, TRIGGER_INGREDIENT_ID, "Zebu", "stale", 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, TRIGGER_INGREDIENT_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDITED_NAME);
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(9)
	void testEditWorkingCopyOnlyTriggerIngredientBumpsItsMirror(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createTriggerIngredient(tx, "Tempeh"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.editTriggerIngredient(tx, newId, "Tempeh strips", "Fermented soy cake.", 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Tempeh strips");
		assertThat(details.explanationForLlm()).isEqualTo("Fermented soy cake.");
		assertThat(details.version()).isEqualTo(2L);
	}

	@Test
	@Order(10)
	void testFindEditableByIdRejectsUnknownTriggerIngredient(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		assertThatThrownBy(() -> factory.withoutTransaction(em -> sut.findEditableById(em, UUID.randomUUID()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);
	}

	@Test
	@Order(11)
	void testFindTranslationsForEditOverlaysStagedOnMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_TI_ID, "Lentils (translation test)")
						.chain(() -> persistTriggerIngredientTranslation(tx, TRANSLATION_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_TI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L));
		assertThat(forEdit.get(RecipeLanguage.LT)).isEqualTo(new ReferenceDetails(null, null, 0L));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_TI_ID).present()).containsExactly(RecipeLanguage.EL);
		assertThat(langs.get(TRANSLATION_TI_ID).staged()).containsExactly(RecipeLanguage.NL);
	}

	@Test
	@Order(12)
	void testStageTranslationSeedsThenCollapsesToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_COLLAPSE_TI_ID, "Chickpeas (translation test)")
						.chain(() -> persistTriggerIngredientTranslation(tx, TRANSLATION_COLLAPSE_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_TI_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged.get(TRANSLATION_COLLAPSE_TI_ID).staged()).containsExactly(RecipeLanguage.EL);

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_COLLAPSE_TI_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_COLLAPSE_TI_ID).present()).containsExactly(RecipeLanguage.EL);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_COLLAPSE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L));
	}

	@Test
	@Order(13)
	void testStageTranslationBumpsThenRejectsStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_BUMP_TI_ID, "Tofu (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_TI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_TI_ID, RecipeLanguage.NL, RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_BUMP_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 2L));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_TI_ID, RecipeLanguage.NL, "Stale.", "x", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	@Order(14)
	void testRevertTranslationRemovesStagedRowRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_REVERT_TI_ID, "Seitan (translation test)")
						.chain(() -> persistTriggerIngredientTranslation(tx, TRANSLATION_REVERT_TI_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_TI_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_TI_ID, RecipeLanguage.EL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L));
		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_REVERT_TI_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_REVERT_TI_ID).present()).containsExactly(RecipeLanguage.EL);
	}

	@Test
	@Order(15)
	void testRevertTranslationRejectsStaleBaseVersionLeavingItIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_REVERT_STALE_TI_ID, "Edamame (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_STALE_TI_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_TI_ID, RecipeLanguage.NL, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillStaged = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillStaged.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_TI_ID, RecipeLanguage.NL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var afterRevert = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(afterRevert.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(null, null, 0L));
	}

	@Test
	@Order(16)
	void testStageNullTranslationWithNoMasterIsANoOp(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistTriggerIngredient(tx, TRANSLATION_NULL_TI_ID, "Quinoa (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_NULL_TI_ID, RecipeLanguage.EL, null, null, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs).doesNotContainKey(TRANSLATION_NULL_TI_ID);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_NULL_TI_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(null, null, 0L));
	}

	private static Uni<Void> persistTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, String name) {
		var entity = new TriggerIngredientEntity();
		entity.setId(id);
		entity.setName(name);
		return tx.persist(entity).replaceWithVoid();
	}

	private static Uni<Void> persistTriggerIngredientTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm) {
		var translation = new TriggerIngredientTranslationEntity();
		translation.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, id));
		translation.setLang(lang);
		translation.setName(name);
		translation.setExplanationForLlm(explanationForLlm);
		return tx.persist(translation).replaceWithVoid();
	}
}
