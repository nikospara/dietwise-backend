package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.ReferenceOption;
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
class RoleOrTechniqueDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID ROLE_OR_TECHNIQUE_ID = UUID.fromString("f56b72c0-4af0-4e4b-99a8-83bd31c6f7d8");
	private static final String NEW_ROLE_OR_TECHNIQUE_NAME = "Binding agent";
	private static final String MASTER_NAME = "steak centerpiece";
	private static final String EDITED_NAME = "centrepiece protein";
	private static final String EDITED_EXPLANATION = "The dish's main, defining ingredient.";

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
	void testFindAllReturnsLocalizedRoleOrTechniqueWhenTranslationExists(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var roles = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var role = roles.stream()
				.filter(r -> r.getId().asUuid().equals(ROLE_OR_TECHNIQUE_ID))
				.findFirst()
				.orElseThrow();

		assertThat(role.getName()).isEqualTo("steak hoofdgerecht");
		assertThat(role.getExplanationForLlm()).contains("Hoofd ingrediënt dat zorgt voor bijt en fungeert als het middelpunt van het bord.");
	}

	@Test
	@Order(2)
	void testListOptionsReturnsMasterEntries(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var options = factory.withoutTransaction(sut::listOptions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).anySatisfy(option -> assertThat(option.id()).isEqualTo(ROLE_OR_TECHNIQUE_ID));
	}

	@Test
	@Order(3)
	void testCreateRoleOrTechniqueStagesAMirrorRowVisibleInListOptions(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createRoleOrTechnique(tx, NEW_ROLE_OR_TECHNIQUE_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(options).contains(new ReferenceOption(newId, NEW_ROLE_OR_TECHNIQUE_NAME));
		assertThat(options).anySatisfy(option -> assertThat(option.id()).isEqualTo(ROLE_OR_TECHNIQUE_ID));
	}

	@Test
	@Order(4)
	void testFindEditableByIdReturnsMasterDetailsWhenUnchanged(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, ROLE_OR_TECHNIQUE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(details.name()).isEqualTo(MASTER_NAME);
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
	}

	@Test
	@Order(5)
	void testEditRoleOrTechniqueSeedsAMirrorRowOnFirstTouch(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, ROLE_OR_TECHNIQUE_ID, EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, ROLE_OR_TECHNIQUE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDITED_NAME);
		assertThat(details.explanationForLlm()).isEqualTo(EDITED_EXPLANATION);
		assertThat(details.version()).isEqualTo(1L);

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).containsEntry(ROLE_OR_TECHNIQUE_ID, EDITED_NAME);
	}

	@Test
	@Order(6)
	void testEditRoleOrTechniqueBackToMasterValuesCollapsesTheMirrorRow(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, ROLE_OR_TECHNIQUE_ID, MASTER_NAME, null, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(ROLE_OR_TECHNIQUE_ID);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, ROLE_OR_TECHNIQUE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(MASTER_NAME);
		assertThat(details.version()).isZero();
	}

	@Test
	@Order(7)
	void testEditRoleOrTechniqueRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, ROLE_OR_TECHNIQUE_ID, EDITED_NAME, EDITED_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, ROLE_OR_TECHNIQUE_ID, "garnish", "stale", 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, ROLE_OR_TECHNIQUE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo(EDITED_NAME);
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(8)
	void testEditWorkingCopyOnlyRoleOrTechniqueBumpsItsMirror(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createRoleOrTechnique(tx, "Emulsifier"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, newId, "Emulsifier base", "Binds fat and water.", 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Emulsifier base");
		assertThat(details.explanationForLlm()).isEqualTo("Binds fat and water.");
		assertThat(details.version()).isEqualTo(2L);
	}
}
