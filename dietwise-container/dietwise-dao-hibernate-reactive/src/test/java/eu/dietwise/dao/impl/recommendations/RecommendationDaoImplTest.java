package eu.dietwise.dao.impl.recommendations;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.BiologicalGender.FEMALE;
import static eu.dietwise.v1.types.BiologicalGender.MALE;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.recommendations.AgeGroupEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class RecommendationDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RECOMMENDATION_1_ID = UUID.fromString("f24f6873-bcc5-4ed0-a1fb-15fa813f0f0a");
	private static final UUID RECOMMENDATION_2_ID = UUID.fromString("a9d13d91-77bb-4875-ba06-e75f010d31d5");
	private static final UUID AGE_GROUP_1_ID = UUID.fromString("c661e7c6-e4ad-4b88-bf40-fd581f537cb2");

	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(POSTGRES_IMAGE);

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
	void testFindRecommendationsFiltersByAgeAndGender(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl(factory);

		Map<Recommendation, BigDecimal> recommendations = sut.findRecommendations(30, FEMALE)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		// according to the test data
		assertThat(recommendations).hasSize(15);
		assertThat(recommendations.get(new RecommendationImpl("Decrease processed meat"))).isEqualByComparingTo("0.21");
		assertThat(recommendations.get(new RecommendationImpl("Decrease red meat"))).isEqualByComparingTo("0.64");
		assertThat(recommendations.get(new RecommendationImpl("Decrease sodium"))).isEqualByComparingTo("0.64");
		assertThat(recommendations.get(new RecommendationImpl("Decrease sugar-sweetened beverages"))).isEqualByComparingTo("0.16");
		assertThat(recommendations.get(new RecommendationImpl("Decrease trans fatty acids"))).isEqualByComparingTo("0.44");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in calcium"))).isEqualByComparingTo("0.06");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fiber"))).isEqualByComparingTo("0.83");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fruits"))).isEqualByComparingTo("2.29");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in legumes"))).isEqualByComparingTo("0.59");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in milk"))).isEqualByComparingTo("0.26");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in nuts and seeds"))).isEqualByComparingTo("1.03");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in omega-6 polyunsaturated fatty acids"))).isEqualByComparingTo("0.63");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in seafood omega-3 fatty acids"))).isEqualByComparingTo("0.69");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in vegetables"))).isEqualByComparingTo("1.32");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in whole grains"))).isEqualByComparingTo("1.27");
	}

	// KEEP THIS LAST! IT MESSES WITH THE DATA
	@Test
	@Order(10)
	void testInsertions(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl(factory);

		factory.withTransaction(tx -> {
			var rec1 = new RecommendationEntity();
			rec1.setId(RECOMMENDATION_1_ID);
			rec1.setName("Increase milk");
			var rec2 = new RecommendationEntity();
			rec2.setId(RECOMMENDATION_2_ID);
			rec2.setName("Eat legumes");

			var ageGroup1 = new AgeGroupEntity();
			ageGroup1.setId(AGE_GROUP_1_ID);
			ageGroup1.setMin(10);
			ageGroup1.setMax(14);

			var recValue1 = makeRecValue(UUID.fromString("b80f78a4-3246-4f96-baf3-a7f377f4f979"), rec1, ageGroup1, FEMALE, "1.11");
			var recValue2 = makeRecValue(UUID.fromString("2cfc37cb-f955-4ca3-b380-c7a669922f95"), rec2, ageGroup1, FEMALE, "2.22");
			var recValue3 = makeRecValue(UUID.fromString("6cadf706-f507-4f08-ac95-b681af070fda"), rec1, ageGroup1, MALE, "3.33");
			var recValue4 = makeRecValue(UUID.fromString("fe136125-a444-4bc2-b83f-b4302c14d029"), rec2, ageGroup1, MALE, "4.44");

			return tx.persistAll(rec1, rec2, ageGroup1)
					.flatMap(ignored -> tx.persistAll(recValue1, recValue2, recValue3, recValue4));
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		Map<Recommendation, BigDecimal> recommendations = sut.findRecommendations(12, FEMALE)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(recommendations).hasSize(2);
		assertThat(recommendations.get(new RecommendationImpl("Increase milk"))).isEqualByComparingTo("1.11");
		assertThat(recommendations.get(new RecommendationImpl("Eat legumes"))).isEqualByComparingTo("2.22");
	}

	private RecommendationValueEntity makeRecValue(UUID id, RecommendationEntity recommendation, AgeGroupEntity ageGroup, BiologicalGender gender, String value) {
		var recommendationValue = new RecommendationValueEntity();
		recommendationValue.setId(id);
		recommendationValue.setRecommendation(recommendation);
		recommendationValue.setAgeGroup(ageGroup);
		recommendationValue.setGender(gender);
		recommendationValue.setValue(new BigDecimal(value));
		return recommendationValue;
	}
}
