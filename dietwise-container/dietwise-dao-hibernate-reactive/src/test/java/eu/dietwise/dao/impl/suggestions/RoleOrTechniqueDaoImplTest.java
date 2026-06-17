package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.EntityNotFoundException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueTranslationEntity;
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
class RoleOrTechniqueDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID ROLE_OR_TECHNIQUE_ID = UUID.fromString("f56b72c0-4af0-4e4b-99a8-83bd31c6f7d8");
	private static final String NEW_ROLE_OR_TECHNIQUE_NAME = "Binding agent";
	private static final String MASTER_NAME = "steak centerpiece";
	private static final String EDITED_NAME = "centrepiece protein";
	private static final String EDITED_EXPLANATION = "The dish's main, defining ingredient.";

	private static final UUID TRANSLATION_ROLE_ID = UUID.fromString("c1d2e3f4-0001-4a5b-8c9d-0e1f2a3b0001");
	private static final UUID TRANSLATION_COLLAPSE_ROLE_ID = UUID.fromString("c1d2e3f4-0002-4a5b-8c9d-0e1f2a3b0002");
	private static final UUID TRANSLATION_BUMP_ROLE_ID = UUID.fromString("c1d2e3f4-0003-4a5b-8c9d-0e1f2a3b0003");
	private static final UUID TRANSLATION_REVERT_ROLE_ID = UUID.fromString("c1d2e3f4-0004-4a5b-8c9d-0e1f2a3b0004");
	private static final UUID TRANSLATION_REVERT_STALE_ROLE_ID = UUID.fromString("c1d2e3f4-0005-4a5b-8c9d-0e1f2a3b0005");
	private static final UUID TRANSLATION_NULL_ROLE_ID = UUID.fromString("c1d2e3f4-0006-4a5b-8c9d-0e1f2a3b0006");
	private static final UUID REVERT_ROLE_ID = UUID.fromString("c1d2e3f4-0007-4a5b-8c9d-0e1f2a3b0007");
	private static final UUID REVERT_STALE_ROLE_ID = UUID.fromString("c1d2e3f4-0008-4a5b-8c9d-0e1f2a3b0008");
	private static final UUID REVERT_NOOP_ROLE_ID = UUID.fromString("c1d2e3f4-0009-4a5b-8c9d-0e1f2a3b0009");
	private static final String MASTER_EL_NAME = "κεντρικό κομμάτι";
	private static final String MASTER_EL_EXPLANATION = "Το κύριο, καθοριστικό συστατικό.";
	private static final String EDITED_EL_NAME = "κεντρικό κομμάτι (αναθ.)";
	private static final String EDITED_EL_EXPLANATION = "Αναθεωρημένη εξήγηση.";
	private static final String STAGED_NL_NAME = "middelpunt";
	private static final String STAGED_NL_EXPLANATION = "Het belangrijkste ingrediënt.";
	private static final String RESTAGED_NL_NAME = "middelpunt herzien";
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
		assertThat(details.published()).isTrue();
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
		assertThat(details.published()).isTrue();

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
		assertThat(details.published()).isFalse();
	}

	@Test
	@Order(9)
	void testFindTranslationsForEditOverlaysStagedOnMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, TRANSLATION_ROLE_ID, "binder (translation test)")
						.chain(() -> persistRoleOrTechniqueTranslation(tx, TRANSLATION_ROLE_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_ROLE_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L, false));
		assertThat(forEdit.get(RecipeLanguage.LT)).isEqualTo(new ReferenceDetails(null, null, 0L, false));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_ROLE_ID).present()).containsExactly(RecipeLanguage.EL);
		assertThat(langs.get(TRANSLATION_ROLE_ID).staged()).containsExactly(RecipeLanguage.NL);
	}

	@Test
	@Order(10)
	void testStageTranslationSeedsThenCollapsesToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, TRANSLATION_COLLAPSE_ROLE_ID, "thickener (translation test)")
						.chain(() -> persistRoleOrTechniqueTranslation(tx, TRANSLATION_COLLAPSE_ROLE_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_ROLE_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged.get(TRANSLATION_COLLAPSE_ROLE_ID).staged()).containsExactly(RecipeLanguage.EL);

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_COLLAPSE_ROLE_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_COLLAPSE_ROLE_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_COLLAPSE_ROLE_ID).present()).containsExactly(RecipeLanguage.EL);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_COLLAPSE_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
	}

	@Test
	@Order(11)
	void testStageTranslationBumpsThenRejectsStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, TRANSLATION_BUMP_ROLE_ID, "garnish (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_ROLE_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_ROLE_ID, RecipeLanguage.NL, RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_BUMP_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(RESTAGED_NL_NAME, RESTAGED_NL_EXPLANATION, 2L, false));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_BUMP_ROLE_ID, RecipeLanguage.NL, "Stale.", "x", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	@Order(12)
	void testRevertTranslationRemovesStagedRowRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, TRANSLATION_REVERT_ROLE_ID, "coating (translation test)")
						.chain(() -> persistRoleOrTechniqueTranslation(tx, TRANSLATION_REVERT_ROLE_ID, RecipeLanguage.EL, MASTER_EL_NAME, MASTER_EL_EXPLANATION)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_ROLE_ID, RecipeLanguage.EL, EDITED_EL_NAME, EDITED_EL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_ROLE_ID, RecipeLanguage.EL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(MASTER_EL_NAME, MASTER_EL_EXPLANATION, 0L, true));
		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs.get(TRANSLATION_REVERT_ROLE_ID).staged()).isEmpty();
		assertThat(langs.get(TRANSLATION_REVERT_ROLE_ID).present()).containsExactly(RecipeLanguage.EL);
	}

	@Test
	@Order(13)
	void testRevertTranslationRejectsStaleBaseVersionLeavingItIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, TRANSLATION_REVERT_STALE_ROLE_ID, "dusting (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_REVERT_STALE_ROLE_ID, RecipeLanguage.NL, STAGED_NL_NAME, STAGED_NL_EXPLANATION, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_ROLE_ID, RecipeLanguage.NL, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var stillStaged = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stillStaged.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(STAGED_NL_NAME, STAGED_NL_EXPLANATION, 1L, false));

		factory.withTransaction(tx -> sut.revertTranslation(tx, TRANSLATION_REVERT_STALE_ROLE_ID, RecipeLanguage.NL, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var afterRevert = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_REVERT_STALE_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(afterRevert.get(RecipeLanguage.NL)).isEqualTo(new ReferenceDetails(null, null, 0L, false));
	}

	@Test
	@Order(14)
	void testStageNullTranslationWithNoMasterIsANoOp(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, TRANSLATION_NULL_ROLE_ID, "rub (translation test)"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageTranslation(tx, TRANSLATION_NULL_ROLE_ID, RecipeLanguage.EL, null, null, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var langs = factory.withoutTransaction(sut::findTranslationLangs)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(langs).doesNotContainKey(TRANSLATION_NULL_ROLE_ID);
		var forEdit = factory.withoutTransaction(em -> sut.findTranslationsForEdit(em, TRANSLATION_NULL_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(forEdit.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails(null, null, 0L, false));
	}

	@Test
	@Order(15)
	void testRevertRoleOrTechniqueRemovesStagedEditRestoringMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, REVERT_ROLE_ID, "thickener"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, REVERT_ROLE_ID, "thickener (edited)", "Thickens sauces.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertRoleOrTechnique(tx, REVERT_ROLE_ID, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("thickener");
		assertThat(details.explanationForLlm()).isNull();
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
		var stagedNames = factory.withoutTransaction(sut::findStagedNames)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stagedNames).doesNotContainKey(REVERT_ROLE_ID);
	}

	@Test
	@Order(16)
	void testRevertRoleOrTechniqueRejectsStaleBaseVersionLeavingTheStagedEditIntact(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, REVERT_STALE_ROLE_ID, "glaze"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.editRoleOrTechnique(tx, REVERT_STALE_ROLE_ID, "glaze (edited)", "Glazes the dish.", 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertRoleOrTechnique(tx, REVERT_STALE_ROLE_ID, 99L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_STALE_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("glaze (edited)");
		assertThat(details.version()).isEqualTo(1L);
	}

	@Test
	@Order(17)
	void testRevertRoleOrTechniqueIsANoOpWhenThereIsNoStagedEdit(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistRoleOrTechnique(tx, REVERT_NOOP_ROLE_ID, "garnish base"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.revertRoleOrTechnique(tx, REVERT_NOOP_ROLE_ID, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, REVERT_NOOP_ROLE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("garnish base");
		assertThat(details.version()).isZero();
		assertThat(details.published()).isTrue();
	}

	@Test
	@Order(18)
	void testRevertWorkingCopyOnlyRoleOrTechniqueIsRefused(Mutiny.SessionFactory sessionFactory) {
		var sut = new RoleOrTechniqueDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createRoleOrTechnique(tx, "Stabiliser"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.revertRoleOrTechnique(tx, newId, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(EntityNotFoundException.class);

		var details = factory.withoutTransaction(em -> sut.findEditableById(em, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(details.name()).isEqualTo("Stabiliser");
		assertThat(details.version()).isEqualTo(1L);
		assertThat(details.published()).isFalse();
	}

	private static Uni<Void> persistRoleOrTechnique(ReactivePersistenceTxContext tx, UUID id, String name) {
		var entity = new RoleOrTechniqueEntity();
		entity.setId(id);
		entity.setName(name);
		return tx.persist(entity).replaceWithVoid();
	}

	private static Uni<Void> persistRoleOrTechniqueTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm) {
		var translation = new RoleOrTechniqueTranslationEntity();
		translation.setRoleOrTechnique(tx.getReference(RoleOrTechniqueEntity.class, id));
		translation.setLang(lang);
		translation.setName(name);
		translation.setExplanationForLlm(explanationForLlm);
		return tx.persist(translation).replaceWithVoid();
	}
}
