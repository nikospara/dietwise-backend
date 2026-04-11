package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueTranslationEntity;
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

		factory.withTransaction(tx -> tx.find(RoleOrTechniqueEntity.class, ROLE_OR_TECHNIQUE_ID)
				.flatMap(roleOrTechnique -> {
					var translation = new RoleOrTechniqueTranslationEntity();
					translation.setRoleOrTechnique(roleOrTechnique);
					translation.setLang(RecipeLanguage.NL);
					translation.setName("biefstuk middelpunt");
					translation.setExplanationForLlm("NL explanation");
					return tx.persist(translation);
				}))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var roles = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var role = roles.stream()
				.filter(r -> r.getId().asUuid().equals(ROLE_OR_TECHNIQUE_ID))
				.findFirst()
				.orElseThrow();

		assertThat(role.getName()).isEqualTo("biefstuk middelpunt");
		assertThat(role.getExplanationForLlm()).contains("NL explanation");
	}
}
