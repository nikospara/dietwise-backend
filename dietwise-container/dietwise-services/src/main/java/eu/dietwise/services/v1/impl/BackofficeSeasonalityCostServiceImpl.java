package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.SeasonalityCostDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.suggestions.AlternativeIngredientCountryKey;
import eu.dietwise.services.model.suggestions.BackofficeAlternativeIngredient;
import eu.dietwise.services.model.suggestions.StagedCost;
import eu.dietwise.services.model.suggestions.StagedSeasonality;
import eu.dietwise.services.v1.BackofficeSeasonalityCostService;
import eu.dietwise.services.v1.types.CostCell;
import eu.dietwise.services.v1.types.SeasonalityCell;
import eu.dietwise.services.v1.types.SeasonalityCostGrid;
import eu.dietwise.services.v1.types.SeasonalityCostRow;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.Seasonality;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeSeasonalityCostServiceImpl implements BackofficeSeasonalityCostService {
	private static final List<Country> COUNTRIES = List.of(Country.values());

	private final AlternativeIngredientDao alternativeIngredientDao;
	private final SeasonalityCostDao seasonalityCostDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeSeasonalityCostServiceImpl(
			AlternativeIngredientDao alternativeIngredientDao,
			SeasonalityCostDao seasonalityCostDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.seasonalityCostDao = seasonalityCostDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<SeasonalityCostGrid> grid(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				alternativeIngredientDao.listForBackoffice(em),
				_ -> seasonalityCostDao.findMasterSeasonality(em),
				_ -> seasonalityCostDao.findStagedSeasonality(em),
				_ -> seasonalityCostDao.findMasterCost(em),
				_ -> seasonalityCostDao.findStagedCost(em),
				this::toGrid
		));
	}

	@Override
	public Uni<Void> stageSeasonality(User user, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo, long baseVersion) {
		authorization.requireAdmin(user);
		validateMonthRange(monthFrom, monthTo);
		return persistenceContextFactory.withTransaction(tx -> seasonalityCostDao.stageSeasonality(tx, alternativeIngredientId, country, monthFrom, monthTo, baseVersion));
	}

	@Override
	public Uni<Void> stageCost(User user, UUID alternativeIngredientId, Country country, Cost cost, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> seasonalityCostDao.stageCost(tx, alternativeIngredientId, country, cost, baseVersion));
	}

	private static void validateMonthRange(Integer monthFrom, Integer monthTo) {
		if ((monthFrom == null) != (monthTo == null)) {
			throw new IllegalArgumentException("A seasonality range must have both months set or both empty");
		}
		if (monthFrom != null && (isNotValidMonth(monthFrom) || isNotValidMonth(monthTo))) {
			throw new IllegalArgumentException("Seasonality months must be between 1 and 12");
		}
	}

	private static boolean isNotValidMonth(int month) {
		return month < 1 || month > 12;
	}

	private SeasonalityCostGrid toGrid(
			List<BackofficeAlternativeIngredient> ingredients,
			Map<AlternativeIngredientCountryKey, Seasonality> masterSeasonality,
			Map<AlternativeIngredientCountryKey, StagedSeasonality> stagedSeasonality,
			Map<AlternativeIngredientCountryKey, Cost> masterCost,
			Map<AlternativeIngredientCountryKey, StagedCost> stagedCost
	) {
		List<SeasonalityCostRow> rows = ingredients.stream()
				.map(ingredient -> toRow(ingredient, masterSeasonality, stagedSeasonality, masterCost, stagedCost))
				.toList();
		return new SeasonalityCostGrid(COUNTRIES, rows);
	}

	private static SeasonalityCostRow toRow(
			BackofficeAlternativeIngredient ingredient,
			Map<AlternativeIngredientCountryKey, Seasonality> masterSeasonality,
			Map<AlternativeIngredientCountryKey, StagedSeasonality> stagedSeasonality,
			Map<AlternativeIngredientCountryKey, Cost> masterCost,
			Map<AlternativeIngredientCountryKey, StagedCost> stagedCost
	) {
		Map<Country, SeasonalityCell> seasonality = new EnumMap<>(Country.class);
		Map<Country, CostCell> cost = new EnumMap<>(Country.class);
		for (Country country : COUNTRIES) {
			var key = new AlternativeIngredientCountryKey(ingredient.id(), country);
			seasonality.put(country, toSeasonalityCell(masterSeasonality.get(key), stagedSeasonality.get(key)));
			cost.put(country, toCostCell(masterCost.get(key), stagedCost.get(key)));
		}
		return new SeasonalityCostRow(ingredient.id(), ingredient.name(), ingredient.published(), seasonality, cost);
	}

	private static SeasonalityCell toSeasonalityCell(Seasonality master, StagedSeasonality staged) {
		if (staged != null) {
			return new SeasonalityCell(staged.monthFrom(), staged.monthTo(), true, staged.version());
		}
		if (master != null) {
			return new SeasonalityCell(master.getMonthFrom(), master.getMonthTo(), false, 0L);
		}
		return new SeasonalityCell(null, null, false, 0L);
	}

	private static CostCell toCostCell(Cost master, StagedCost staged) {
		if (staged != null) {
			return new CostCell(staged.cost(), true, staged.version());
		}
		if (master != null) {
			return new CostCell(master, false, 0L);
		}
		return new CostCell(null, false, 0L);
	}
}
