package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.Cost.HI;
import static eu.dietwise.v1.types.Cost.LO;
import static eu.dietwise.v1.types.Cost.MED;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.services.model.suggestions.AlternativeIngredientCountryKey;
import eu.dietwise.services.model.suggestions.StagedCost;
import eu.dietwise.services.model.suggestions.StagedSeasonality;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.ImmutableSeasonality;
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
class SeasonalityCostDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID SEASONALITY_MASTER_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000001");
	private static final UUID SEASONALITY_SEED_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000002");
	private static final UUID SEASONALITY_SEED_STALE_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000003");
	private static final UUID SEASONALITY_BUMP_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000004");
	private static final UUID SEASONALITY_COLLAPSE_EMPTY_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000005");
	private static final UUID SEASONALITY_OVER_MASTER_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000006");
	private static final UUID SEASONALITY_CLEAR_MASTER_AI_ID = UUID.fromString("5ea50000-0000-0000-0000-000000000007");

	private static final UUID COST_MASTER_AI_ID = UUID.fromString("c0570000-0000-0000-0000-000000000001");
	private static final UUID COST_SEED_AI_ID = UUID.fromString("c0570000-0000-0000-0000-000000000002");
	private static final UUID COST_BUMP_AI_ID = UUID.fromString("c0570000-0000-0000-0000-000000000003");
	private static final UUID COST_COLLAPSE_AI_ID = UUID.fromString("c0570000-0000-0000-0000-000000000004");
	private static final UUID COST_CLEAR_MASTER_AI_ID = UUID.fromString("c0570000-0000-0000-0000-000000000005");

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
	void findMasterSeasonalityReturnsPublishedRowsByCountry(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_MASTER_AI_ID, "Seasonality master cream")
						.chain(() -> persistMasterSeasonality(tx, SEASONALITY_MASTER_AI_ID, GREECE, 8, 10))
						.chain(() -> persistMasterSeasonality(tx, SEASONALITY_MASTER_AI_ID, BELGIUM, 7, 9)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var master = factory.withoutTransaction(sut::findMasterSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(master).containsEntry(key(SEASONALITY_MASTER_AI_ID, GREECE), seasonality(8, 10));
		assertThat(master).containsEntry(key(SEASONALITY_MASTER_AI_ID, BELGIUM), seasonality(7, 9));
	}

	@Test
	@Order(2)
	void stageSeasonalitySeedsAMirrorRowOnFirstTouch(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_SEED_AI_ID, "Seasonality seed cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_SEED_AI_ID, GREECE, 3, 5, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(SEASONALITY_SEED_AI_ID, GREECE), new StagedSeasonality(3, 5, 1L));
	}

	@Test
	@Order(3)
	void stageSeasonalityRejectsANonZeroBaseVersionWhenNoStagedRowExists(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_SEED_STALE_AI_ID, "Seasonality seed-stale cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_SEED_STALE_AI_ID, GREECE, 3, 5, 7L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);

		var staged = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).doesNotContainKey(key(SEASONALITY_SEED_STALE_AI_ID, GREECE));
	}

	@Test
	@Order(4)
	void stageSeasonalityBumpsThenRejectsStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_BUMP_AI_ID, "Seasonality bump cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_BUMP_AI_ID, GREECE, 3, 5, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_BUMP_AI_ID, GREECE, 11, 2, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(SEASONALITY_BUMP_AI_ID, GREECE), new StagedSeasonality(11, 2, 2L));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_BUMP_AI_ID, GREECE, 4, 6, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	@Order(5)
	void stageSeasonalityBackToEmptyMasterCollapsesTheMirrorRow(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_COLLAPSE_EMPTY_AI_ID, "Seasonality collapse-empty cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_COLLAPSE_EMPTY_AI_ID, GREECE, 3, 5, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_COLLAPSE_EMPTY_AI_ID, GREECE, null, null, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).doesNotContainKey(key(SEASONALITY_COLLAPSE_EMPTY_AI_ID, GREECE));
	}

	@Test
	@Order(6)
	void stageSeasonalityOverNonEmptyMasterSeedsThenCollapsesBackToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_OVER_MASTER_AI_ID, "Seasonality over-master cream")
						.chain(() -> persistMasterSeasonality(tx, SEASONALITY_OVER_MASTER_AI_ID, GREECE, 3, 5)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_OVER_MASTER_AI_ID, GREECE, 6, 8, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(SEASONALITY_OVER_MASTER_AI_ID, GREECE), new StagedSeasonality(6, 8, 1L));

		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_OVER_MASTER_AI_ID, GREECE, 3, 5, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var collapsed = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(collapsed).doesNotContainKey(key(SEASONALITY_OVER_MASTER_AI_ID, GREECE));
	}

	@Test
	@Order(7)
	void stageSeasonalityEmptyOverNonEmptyMasterSeedsAStagedEmpty(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, SEASONALITY_CLEAR_MASTER_AI_ID, "Seasonality clear-master cream")
						.chain(() -> persistMasterSeasonality(tx, SEASONALITY_CLEAR_MASTER_AI_ID, GREECE, 3, 5)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageSeasonality(tx, SEASONALITY_CLEAR_MASTER_AI_ID, GREECE, null, null, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedSeasonality).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(SEASONALITY_CLEAR_MASTER_AI_ID, GREECE), new StagedSeasonality(null, null, 1L));
	}

	@Test
	@Order(8)
	void findMasterCostReturnsPublishedRowsByCountry(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, COST_MASTER_AI_ID, "Cost master cream")
						.chain(() -> persistMasterCost(tx, COST_MASTER_AI_ID, GREECE, LO))
						.chain(() -> persistMasterCost(tx, COST_MASTER_AI_ID, BELGIUM, HI)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var master = factory.withoutTransaction(sut::findMasterCost).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(master).containsEntry(key(COST_MASTER_AI_ID, GREECE), LO);
		assertThat(master).containsEntry(key(COST_MASTER_AI_ID, BELGIUM), HI);
	}

	@Test
	@Order(9)
	void stageCostSeedsAMirrorRowOnFirstTouch(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, COST_SEED_AI_ID, "Cost seed cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageCost(tx, COST_SEED_AI_ID, GREECE, MED, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedCost).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(COST_SEED_AI_ID, GREECE), new StagedCost(MED, 1L));
	}

	@Test
	@Order(10)
	void stageCostBumpsThenRejectsStaleBaseVersion(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, COST_BUMP_AI_ID, "Cost bump cream"))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		factory.withTransaction(tx -> sut.stageCost(tx, COST_BUMP_AI_ID, GREECE, LO, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageCost(tx, COST_BUMP_AI_ID, GREECE, HI, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedCost).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(COST_BUMP_AI_ID, GREECE), new StagedCost(HI, 2L));

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.stageCost(tx, COST_BUMP_AI_ID, GREECE, MED, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	@Order(11)
	void stageCostOverNonEmptyMasterSeedsThenCollapsesBackToMaster(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, COST_COLLAPSE_AI_ID, "Cost collapse cream")
						.chain(() -> persistMasterCost(tx, COST_COLLAPSE_AI_ID, GREECE, LO)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageCost(tx, COST_COLLAPSE_AI_ID, GREECE, HI, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var staged = factory.withoutTransaction(sut::findStagedCost).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(COST_COLLAPSE_AI_ID, GREECE), new StagedCost(HI, 1L));

		factory.withTransaction(tx -> sut.stageCost(tx, COST_COLLAPSE_AI_ID, GREECE, LO, 1L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var collapsed = factory.withoutTransaction(sut::findStagedCost).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(collapsed).doesNotContainKey(key(COST_COLLAPSE_AI_ID, GREECE));
	}

	@Test
	@Order(12)
	void stageCostEmptyOverNonEmptyMasterSeedsAStagedEmpty(Mutiny.SessionFactory sessionFactory) {
		var sut = new SeasonalityCostDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> persistAlternativeIngredient(tx, COST_CLEAR_MASTER_AI_ID, "Cost clear-master cream")
						.chain(() -> persistMasterCost(tx, COST_CLEAR_MASTER_AI_ID, GREECE, HI)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		factory.withTransaction(tx -> sut.stageCost(tx, COST_CLEAR_MASTER_AI_ID, GREECE, null, 0L))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var staged = factory.withoutTransaction(sut::findStagedCost).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(staged).containsEntry(key(COST_CLEAR_MASTER_AI_ID, GREECE), new StagedCost(null, 1L));
	}

	private static Uni<Void> persistAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id, String name) {
		var entity = new AlternativeIngredientEntity();
		entity.setId(id);
		entity.setName(name);
		return tx.persist(entity).replaceWithVoid();
	}

	private static Uni<Void> persistMasterSeasonality(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, int monthFrom, int monthTo) {
		var entity = new AlternativeIngredientSeasonalityEntity();
		entity.setAlternativeIngredient(tx.getReference(AlternativeIngredientEntity.class, alternativeIngredientId));
		entity.setCountry(country);
		entity.setMonthFrom(monthFrom);
		entity.setMonthTo(monthTo);
		return tx.persist(entity).replaceWithVoid();
	}

	private static Uni<Void> persistMasterCost(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Cost cost) {
		var entity = new AlternativeIngredientCostEntity();
		entity.setAlternativeIngredient(tx.getReference(AlternativeIngredientEntity.class, alternativeIngredientId));
		entity.setCountry(country);
		entity.setCost(cost);
		return tx.persist(entity).replaceWithVoid();
	}

	private static AlternativeIngredientCountryKey key(UUID alternativeIngredientId, Country country) {
		return new AlternativeIngredientCountryKey(alternativeIngredientId, country);
	}

	private static Seasonality seasonality(int monthFrom, int monthTo) {
		return ImmutableSeasonality.builder().monthFrom(monthFrom).monthTo(monthTo).build();
	}
}
