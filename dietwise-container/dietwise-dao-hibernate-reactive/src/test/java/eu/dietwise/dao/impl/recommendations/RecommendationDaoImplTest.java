package eu.dietwise.dao.impl.recommendations;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.BiologicalGender.FEMALE;
import static eu.dietwise.v1.types.BiologicalGender.MALE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.dao.jpa.recommendations.AgeGroupEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.recommendations.RecommendationValueEntity;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.recommendations.ExplanationOverride;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecommendationWeight;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import org.assertj.core.data.Percentage;
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
public class RecommendationDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RECOMMENDATION_1_ID = UUID.fromString("f24f6873-bcc5-4ed0-a1fb-15fa813f0f0a");
	private static final UUID RECOMMENDATION_2_ID = UUID.fromString("a9d13d91-77bb-4875-ba06-e75f010d31d5");
	private static final UUID AGE_GROUP_1_ID = UUID.fromString("c661e7c6-e4ad-4b88-bf40-fd581f537cb2");

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
	void testFindRecommendationsFiltersByAgeAndGender(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		Map<Recommendation, BigDecimal> recommendations =
				factory.withoutTransaction(em -> sut.findRecommendations(em, 30, FEMALE))
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

	@Test
	@Order(2)
	void testFindRecommendationsFiltersByGender(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		Map<Recommendation, BigDecimal> recommendations =
				factory.withoutTransaction(em -> sut.findRecommendations(em, MALE))
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		// according to the test data
		assertThat(recommendations).hasSize(15);
		assertThat(recommendations.get(new RecommendationImpl("Decrease processed meat"))).isCloseTo(new BigDecimal("0.56333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Decrease red meat"))).isCloseTo(new BigDecimal("0.54333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Decrease sodium"))).isCloseTo(new BigDecimal("3.16666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Decrease sugar-sweetened beverages"))).isEqualByComparingTo("0.34");
		assertThat(recommendations.get(new RecommendationImpl("Decrease trans fatty acids"))).isCloseTo(new BigDecimal("0.79666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in calcium"))).isEqualByComparingTo("0.11");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fiber"))).isCloseTo(new BigDecimal("0.85666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fruits"))).isCloseTo(new BigDecimal("3.32333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in legumes"))).isCloseTo(new BigDecimal("1.07666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in milk"))).isCloseTo(new BigDecimal("0.19333"), Percentage.withPercentage(0.01));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in nuts and seeds"))).isCloseTo(new BigDecimal("1.70666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in omega-6 polyunsaturated fatty acids"))).isCloseTo(new BigDecimal("1.04333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in seafood omega-3 fatty acids"))).isCloseTo(new BigDecimal("0.92666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in vegetables"))).isCloseTo(new BigDecimal("1.57666"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in whole grains"))).isEqualByComparingTo("2.16");
	}

	@Test
	@Order(3)
	void testFindRecommendationsFiltersByAge(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		Map<Recommendation, BigDecimal> recommendations =
				factory.withoutTransaction(em -> sut.findRecommendations(em, 50))
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		// according to the test data
		assertThat(recommendations).hasSize(15);
		assertThat(recommendations.get(new RecommendationImpl("Decrease processed meat"))).isEqualByComparingTo("0.655");
		assertThat(recommendations.get(new RecommendationImpl("Decrease red meat"))).isEqualByComparingTo("0.81");
		assertThat(recommendations.get(new RecommendationImpl("Decrease sodium"))).isEqualByComparingTo("3.6");
		assertThat(recommendations.get(new RecommendationImpl("Decrease sugar-sweetened beverages"))).isEqualByComparingTo("0.355");
		assertThat(recommendations.get(new RecommendationImpl("Decrease trans fatty acids"))).isEqualByComparingTo("0.875");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in calcium"))).isEqualByComparingTo("0.195");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fiber"))).isEqualByComparingTo("0.9");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fruits"))).isEqualByComparingTo("3.92");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in legumes"))).isEqualByComparingTo("1.2");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in milk"))).isEqualByComparingTo("0.38");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in nuts and seeds"))).isEqualByComparingTo("1.87");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in omega-6 polyunsaturated fatty acids"))).isEqualByComparingTo("1.19");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in seafood omega-3 fatty acids"))).isEqualByComparingTo("1.08");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in vegetables"))).isEqualByComparingTo("1.925");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in whole grains"))).isEqualByComparingTo("2.6");
	}

	@Test
	@Order(4)
	void testFindRecommendationsFiltersWhenNoPersonalInfo(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		Map<Recommendation, BigDecimal> recommendations =
				factory.withoutTransaction(sut::findRecommendations)
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		// according to the test data
		assertThat(recommendations).hasSize(15);
		assertThat(recommendations.get(new RecommendationImpl("Decrease processed meat"))).isCloseTo(new BigDecimal("0.49666"), Percentage.withPercentage(0.01));
		assertThat(recommendations.get(new RecommendationImpl("Decrease red meat"))).isCloseTo(new BigDecimal("0.62833"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Decrease sodium"))).isCloseTo(new BigDecimal("2.575"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Decrease sugar-sweetened beverages"))).isEqualByComparingTo("0.28");
		assertThat(recommendations.get(new RecommendationImpl("Decrease trans fatty acids"))).isCloseTo(new BigDecimal("0.69"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in calcium"))).isEqualByComparingTo("0.15");
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fiber"))).isCloseTo(new BigDecimal("0.84833"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in fruits"))).isCloseTo(new BigDecimal("3.10333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in legumes"))).isCloseTo(new BigDecimal("1.00333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in milk"))).isCloseTo(new BigDecimal("0.27833"), Percentage.withPercentage(0.01));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in nuts and seeds"))).isCloseTo(new BigDecimal("1.54833"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in omega-6 polyunsaturated fatty acids"))).isCloseTo(new BigDecimal("0.925"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in seafood omega-3 fatty acids"))).isCloseTo(new BigDecimal("0.92333"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in vegetables"))).isCloseTo(new BigDecimal("1.62833"), Percentage.withPercentage(0.001));
		assertThat(recommendations.get(new RecommendationImpl("Diet low in whole grains"))).isCloseTo(new BigDecimal("2.03166"), Percentage.withPercentage(0.001));
	}

	@Test
	@Order(5)
	void testListAllRecommendationsForScoring(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		List<RecommendationComponent> recommendations =
				factory.withoutTransaction(em -> sut.listAllRecommendationsForScoring(em, RecipeLanguage.EN))
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		// according to the test data
		assertThat(recommendations).hasSize(15);
		assertThat(recommendations).anyMatch(rc ->
				rc.getRecommendation().asString().equals("Decrease processed meat")
						&& rc.getComponentForScoring().asString().equals("processed meat")
						&& rc.getWeight() == RecommendationWeight.LIMITED);
	}

	@Test
	@Order(6)
	void testListAllRecommendationsForScoringReturnsLocalizedValuesWhenTranslationExists(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		List<RecommendationComponent> recommendations =
				factory.withoutTransaction(em -> sut.listAllRecommendationsForScoring(em, RecipeLanguage.NL))
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(recommendations).anyMatch(rc ->
				rc.getRecommendation().asString().equals("Voedingspatroon laag in calcium")
						&& rc.getComponentForScoring().asString().equals("calcium"));
	}

	@Test
	@Order(7)
	void testListOptionsReturnsEveryRecommendationAsIdAndNameSortedByName(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		List<ReferenceOption> options =
				factory.withoutTransaction(sut::listOptions)
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).hasSize(15);
		assertThat(options).extracting(ReferenceOption::name).contains("Decrease red meat");
		assertThat(options).allSatisfy(option -> {
			assertThat(option.id()).isNotNull();
			assertThat(option.name()).isNotBlank();
		});
		assertThat(options).isSortedAccordingTo(Comparator.comparing(ReferenceOption::name));
	}

	@Test
	@Order(8)
	void testListForBackofficeReturnsEveryRecommendationsMasterFieldsSortedByName(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		List<BackofficeRecommendation> rows =
				factory.withoutTransaction(sut::listForBackoffice)
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rows).hasSize(15);
		assertThat(rows).extracting(BackofficeRecommendation::name).isSortedAccordingTo(Comparator.naturalOrder());
		var processedMeat = rows.stream().filter(r -> r.name().equals("Decrease processed meat")).findFirst().orElseThrow();
		assertThat(processedMeat.id()).isNotNull();
		assertThat(processedMeat.componentForScoring()).isEqualTo("processed meat");
		assertThat(processedMeat.weight()).isEqualTo(RecommendationWeight.LIMITED);
	}

	@Test
	@Order(9)
	void testFindTranslationLangsReportsPresentMasterLanguagesWithNothingStaged(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		UUID calciumId = factory.withoutTransaction(sut::listForBackoffice)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
				.stream().filter(r -> r.name().equals("Diet low in calcium")).map(BackofficeRecommendation::id).findFirst().orElseThrow();

		Map<UUID, TranslationLangs> langs =
				factory.withoutTransaction(sut::findTranslationLangs)
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(langs).containsKey(calciumId);
		assertThat(langs.get(calciumId).present()).contains(RecipeLanguage.NL);
		assertThat(langs.get(calciumId).staged()).isEmpty();
	}

	@Test
	@Order(10)
	void stageExplanationSeedsAWorkingCopyRowOverlaidByFindExplanationOverridesAndRevertRemovesIt(Mutiny.SessionFactory sessionFactory) {
		UUID id = lookupId(sessionFactory, "Decrease red meat");

		long version = stage(sessionFactory, id, "Staged red-meat explanation.", 0L);
		assertThat(version).isEqualTo(1L);

		Map<UUID, ExplanationOverride> overrides = overrides(sessionFactory);
		assertThat(overrides).containsKey(id);
		assertThat(overrides.get(id).explanationForLlm()).isEqualTo("Staged red-meat explanation.");
		assertThat(overrides.get(id).version()).isEqualTo(1L);

		revert(sessionFactory, id, 1L);
		assertThat(overrides(sessionFactory)).doesNotContainKey(id);
	}

	@Test
	@Order(11)
	void stageExplanationTwiceBumpsTheVersion(Mutiny.SessionFactory sessionFactory) {
		UUID id = lookupId(sessionFactory, "Decrease sodium");

		assertThat(stage(sessionFactory, id, "First.", 0L)).isEqualTo(1L);
		assertThat(stage(sessionFactory, id, "Second.", 1L)).isEqualTo(2L);

		ExplanationOverride override = overrides(sessionFactory).get(id);
		assertThat(override.explanationForLlm()).isEqualTo("Second.");
		assertThat(override.version()).isEqualTo(2L);

		revert(sessionFactory, id, 2L);
	}

	@Test
	@Order(12)
	void stageExplanationMatchingMasterCollapsesTheWorkingCopyRow(Mutiny.SessionFactory sessionFactory) {
		BackofficeRecommendation master = lookup(sessionFactory, "Decrease sugar-sweetened beverages");

		assertThat(stage(sessionFactory, master.id(), "A staged value.", 0L)).isEqualTo(1L);
		assertThat(stage(sessionFactory, master.id(), master.explanationForLlm(), 1L)).isEqualTo(0L);

		assertThat(overrides(sessionFactory)).doesNotContainKey(master.id());
	}

	@Test
	@Order(13)
	void stageExplanationWithAStaleBaseVersionIsRejected(Mutiny.SessionFactory sessionFactory) {
		UUID id = lookupId(sessionFactory, "Decrease trans fatty acids");
		stage(sessionFactory, id, "Staged.", 0L);

		assertThatThrownBy(() -> stage(sessionFactory, id, "Conflicting.", 0L))
				.isInstanceOf(StaleVersionException.class);

		revert(sessionFactory, id, 1L);
		assertThat(overrides(sessionFactory)).doesNotContainKey(id);
	}

	@Test
	@Order(14)
	void revertExplanationWithAStaleBaseVersionIsRejected(Mutiny.SessionFactory sessionFactory) {
		UUID id = lookupId(sessionFactory, "Diet low in fiber");
		stage(sessionFactory, id, "Staged.", 0L);

		assertThatThrownBy(() -> revert(sessionFactory, id, 99L))
				.isInstanceOf(StaleVersionException.class);

		revert(sessionFactory, id, 1L);
		assertThat(overrides(sessionFactory)).doesNotContainKey(id);
	}

	@Test
	@Order(15)
	void revertExplanationWithoutAStagedChangeIsANoOp(Mutiny.SessionFactory sessionFactory) {
		UUID id = lookupId(sessionFactory, "Diet low in fruits");

		revert(sessionFactory, id, 0L);

		assertThat(overrides(sessionFactory)).doesNotContainKey(id);
	}

	private static BackofficeRecommendation lookup(Mutiny.SessionFactory sessionFactory, String name) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();
		return factory.withoutTransaction(sut::listForBackoffice)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
				.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
	}

	private static UUID lookupId(Mutiny.SessionFactory sessionFactory, String name) {
		return lookup(sessionFactory, name).id();
	}

	private static long stage(Mutiny.SessionFactory sessionFactory, UUID id, String explanationForLlm, long baseVersion) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();
		return factory.withTransaction(tx -> sut.stageExplanation(tx, id, explanationForLlm, baseVersion))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static void revert(Mutiny.SessionFactory sessionFactory, UUID id, long baseVersion) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();
		factory.withTransaction(tx -> sut.revertExplanation(tx, id, baseVersion))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static Map<UUID, ExplanationOverride> overrides(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();
		return factory.withoutTransaction(sut::findExplanationOverrides)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	// KEEP THIS LAST! IT MESSES WITH THE DATA
	@Test
	@Order(20)
	void testInsertions(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new RecommendationDaoImpl();

		factory.withTransaction(tx -> {
			var rec1 = new RecommendationEntity();
			rec1.setId(RECOMMENDATION_1_ID);
			rec1.setName("Increase healthy thing 1");
			rec1.setComponentForScoring("healthy thing 1");
			rec1.setWeight(RecommendationWeight.ENCOURAGED);
			var rec2 = new RecommendationEntity();
			rec2.setId(RECOMMENDATION_2_ID);
			rec2.setName("Decrease unhealthy thing 2");
			rec2.setComponentForScoring("unhealthy thing 2");
			rec2.setWeight(RecommendationWeight.LIMITED);

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

		Map<Recommendation, BigDecimal> recommendations =
				factory.withoutTransaction(em -> sut.findRecommendations(em, 12, FEMALE))
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(recommendations).hasSize(2);
		assertThat(recommendations.get(new RecommendationImpl("Increase healthy thing 1"))).isEqualByComparingTo("1.11");
		assertThat(recommendations.get(new RecommendationImpl("Decrease unhealthy thing 2"))).isEqualByComparingTo("2.22");
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
