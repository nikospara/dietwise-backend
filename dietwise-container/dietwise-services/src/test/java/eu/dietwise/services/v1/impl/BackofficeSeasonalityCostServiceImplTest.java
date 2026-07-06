package eu.dietwise.services.v1.impl;

import static eu.dietwise.v1.types.Cost.HI;
import static eu.dietwise.v1.types.Cost.LO;
import static eu.dietwise.v1.types.Cost.MED;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static eu.dietwise.v1.types.Country.LITHUANIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.SeasonalityCostDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.suggestions.AlternativeIngredientCountryKey;
import eu.dietwise.services.model.suggestions.BackofficeAlternativeIngredient;
import eu.dietwise.services.model.suggestions.StagedCost;
import eu.dietwise.services.model.suggestions.StagedSeasonality;
import eu.dietwise.services.v1.types.CostCell;
import eu.dietwise.services.v1.types.SeasonalityCell;
import eu.dietwise.services.v1.types.SeasonalityCostGrid;
import eu.dietwise.services.v1.types.SeasonalityCostRow;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.ImmutableSeasonality;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackofficeSeasonalityCostServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);

	private static final UUID AI_A_ID = UUID.fromString("a1a2a3a4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID AI_B_ID = UUID.fromString("a1a2a3a4-0002-4f5a-8b9c-0d1e2f3a0002");

	@Mock
	private AlternativeIngredientDao alternativeIngredientDao;

	@Mock
	private SeasonalityCostDao seasonalityCostDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void gridOverlaysStagedOnMasterPerCountryForAnAdmin() {
		when(alternativeIngredientDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeAlternativeIngredient(AI_A_ID, "Lentils", true, 0L),
				new BackofficeAlternativeIngredient(AI_B_ID, "Tofu", false, 1L))));
		when(seasonalityCostDao.findMasterSeasonality(any())).thenReturn(Uni.createFrom().item(Map.of(
				key(AI_A_ID, GREECE), ImmutableSeasonality.builder().monthFrom(3).monthTo(5).build())));
		when(seasonalityCostDao.findStagedSeasonality(any())).thenReturn(Uni.createFrom().item(Map.of(
				key(AI_A_ID, BELGIUM), new StagedSeasonality(6, 8, 1L),
				key(AI_B_ID, GREECE), new StagedSeasonality(null, null, 2L))));
		when(seasonalityCostDao.findMasterCost(any())).thenReturn(Uni.createFrom().item(Map.of(
				key(AI_A_ID, GREECE), LO)));
		when(seasonalityCostDao.findStagedCost(any())).thenReturn(Uni.createFrom().item(Map.of(
				key(AI_A_ID, BELGIUM), new StagedCost(HI, 1L))));

		SeasonalityCostGrid grid = newService().grid(adminUser()).await().atMost(AWAIT);

		assertThat(grid.countries()).containsExactly(BELGIUM, GREECE, LITHUANIA);
		Map<UUID, SeasonalityCostRow> byId = grid.rows().stream().collect(Collectors.toMap(SeasonalityCostRow::id, r -> r));

		SeasonalityCostRow lentils = byId.get(AI_A_ID);
		assertThat(lentils.name()).isEqualTo("Lentils");
		assertThat(lentils.published()).isTrue();
		assertThat(lentils.seasonality().get(BELGIUM)).isEqualTo(new SeasonalityCell(6, 8, true, 1L));
		assertThat(lentils.seasonality().get(GREECE)).isEqualTo(new SeasonalityCell(3, 5, false, 0L));
		assertThat(lentils.seasonality().get(LITHUANIA)).isEqualTo(new SeasonalityCell(null, null, false, 0L));
		assertThat(lentils.cost().get(BELGIUM)).isEqualTo(new CostCell(HI, true, 1L));
		assertThat(lentils.cost().get(GREECE)).isEqualTo(new CostCell(LO, false, 0L));
		assertThat(lentils.cost().get(LITHUANIA)).isEqualTo(new CostCell(null, false, 0L));

		SeasonalityCostRow tofu = byId.get(AI_B_ID);
		assertThat(tofu.published()).isFalse();
		assertThat(tofu.seasonality().get(GREECE)).isEqualTo(new SeasonalityCell(null, null, true, 2L));
		assertThat(tofu.seasonality().get(BELGIUM)).isEqualTo(new SeasonalityCell(null, null, false, 0L));
		assertThat(tofu.cost().get(GREECE)).isEqualTo(new CostCell(null, false, 0L));

		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void gridRejectsANonAdminWithoutReadingData() {
		assertThatThrownBy(() -> newService().grid(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).listForBackoffice(any());
		verify(seasonalityCostDao, never()).findMasterSeasonality(any());
		verify(seasonalityCostDao, never()).findStagedSeasonality(any());
		verify(seasonalityCostDao, never()).findMasterCost(any());
		verify(seasonalityCostDao, never()).findStagedCost(any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageSeasonalityStagesInATransactionForAnAdmin() {
		when(seasonalityCostDao.stageSeasonality(any(), eq(AI_A_ID), eq(GREECE), eq(11), eq(2), eq(0L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageSeasonality(adminUser(), AI_A_ID, GREECE, 11, 2, 0L).await().atMost(AWAIT);

		verify(seasonalityCostDao).stageSeasonality(any(), eq(AI_A_ID), eq(GREECE), eq(11), eq(2), eq(0L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageSeasonalityClearsToEmptyForAnAdmin() {
		when(seasonalityCostDao.stageSeasonality(any(), eq(AI_A_ID), eq(GREECE), eq((Integer) null), eq((Integer) null), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageSeasonality(adminUser(), AI_A_ID, GREECE, null, null, 1L).await().atMost(AWAIT);

		verify(seasonalityCostDao).stageSeasonality(any(), eq(AI_A_ID), eq(GREECE), eq((Integer) null), eq((Integer) null), eq(1L));
	}

	@Test
	void stageSeasonalityRejectsAMonthOutOfRangeWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageSeasonality(adminUser(), AI_A_ID, GREECE, 1, 13, 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(seasonalityCostDao, never()).stageSeasonality(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageSeasonalityRejectsAHalfOpenRangeWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageSeasonality(adminUser(), AI_A_ID, GREECE, 5, null, 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(seasonalityCostDao, never()).stageSeasonality(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageSeasonalityRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageSeasonality(nonAdminUser(), AI_A_ID, GREECE, 3, 5, 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(seasonalityCostDao, never()).stageSeasonality(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageCostStagesInATransactionForAnAdmin() {
		when(seasonalityCostDao.stageCost(any(), eq(AI_A_ID), eq(GREECE), eq(MED), eq(0L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageCost(adminUser(), AI_A_ID, GREECE, MED, 0L).await().atMost(AWAIT);

		verify(seasonalityCostDao).stageCost(any(), eq(AI_A_ID), eq(GREECE), eq(MED), eq(0L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageCostRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageCost(nonAdminUser(), AI_A_ID, GREECE, MED, 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(seasonalityCostDao, never()).stageCost(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private BackofficeSeasonalityCostServiceImpl newService() {
		return new BackofficeSeasonalityCostServiceImpl(
				alternativeIngredientDao, seasonalityCostDao, persistenceContextFactory, new AuthorizationImpl());
	}

	private static AlternativeIngredientCountryKey key(UUID alternativeIngredientId, Country country) {
		return new AlternativeIngredientCountryKey(alternativeIngredientId, country);
	}

	private static User adminUser() {
		return user(EnumSet.of(Role.ADMIN));
	}

	private static User nonAdminUser() {
		return user(EnumSet.of(Role.CITIZEN));
	}

	private static User user(EnumSet<Role> roles) {
		return ImmutableUser.builder()
				.id(new UserIdImpl("00000000-0000-0000-0000-000000000009"))
				.name("editor@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(roles)
				.build();
	}
}
