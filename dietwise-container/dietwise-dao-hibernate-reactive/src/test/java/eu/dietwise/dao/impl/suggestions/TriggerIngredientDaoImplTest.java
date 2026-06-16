package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import jakarta.persistence.criteria.CriteriaDelete;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity_;
import eu.dietwise.v1.types.RecipeLanguage;
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
}
