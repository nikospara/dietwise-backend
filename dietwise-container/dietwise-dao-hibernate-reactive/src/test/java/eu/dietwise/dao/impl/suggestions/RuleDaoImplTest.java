package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
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

		var rules = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID))
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).hasSize(3);
		assertThat(rules).allSatisfy(suggestion -> {
			assertThat(suggestion.getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
			assertThat(suggestion.getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
		});
		assertThat(rules.stream().map(Rule::getRoleOrTechnique).map(RepresentableAsString::asString).collect(Collectors.toSet()))
				.containsExactlyInAnyOrder("minced in sauce", "cubes stew", "steak centerpiece");
	}
}
