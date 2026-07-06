package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.CostCell;
import eu.dietwise.services.v1.types.SeasonalityCell;
import eu.dietwise.services.v1.types.SeasonalityCostGrid;
import eu.dietwise.services.v1.types.SeasonalityCostRow;
import eu.dietwise.v1.types.Country;

/**
 * The Seasonality &amp; Cost grid as returned to the backoffice: the {@code countries} (ISO alpha-2 codes, in display
 * order) and one {@code rows} entry per Alternative Ingredient (sorted by name). Each row carries its effective name,
 * whether a published master row exists, and its per-country seasonality and cost cells keyed by country code.
 */
public record SeasonalityCostGridResponse(
		List<String> countries,
		List<Row> rows
) {
	/**
	 * One grid row: an Alternative Ingredient's id, effective name, whether a published master row exists, and its
	 * per-country seasonality and cost cells keyed by ISO alpha-2 country code.
	 */
	public record Row(
			String id,
			String name,
			boolean published,
			Map<String, SeasonalityCellResponse> seasonality,
			Map<String, CostCellResponse> cost
	) {
		static Row from(SeasonalityCostRow row) {
			return new Row(
					row.id().toString(),
					row.name(),
					row.published(),
					mapByCountryCode(row.seasonality(), SeasonalityCellResponse::from),
					mapByCountryCode(row.cost(), CostCellResponse::from));
		}
	}

	/**
	 * One seasonality cell: the effective in-season month range (both months null means no seasonality), whether it
	 * carries a Staged Change, and the Working Copy version a subsequent edit must be based on.
	 */
	public record SeasonalityCellResponse(Integer monthFrom, Integer monthTo, boolean staged, long version) {
		static SeasonalityCellResponse from(SeasonalityCell cell) {
			return new SeasonalityCellResponse(cell.monthFrom(), cell.monthTo(), cell.staged(), cell.version());
		}
	}

	/**
	 * One cost cell: the effective indicative cost name (null means no cost), whether it carries a Staged Change, and the
	 * Working Copy version a subsequent edit must be based on.
	 */
	public record CostCellResponse(String cost, boolean staged, long version) {
		static CostCellResponse from(CostCell cell) {
			return new CostCellResponse(cell.cost() == null ? null : cell.cost().name(), cell.staged(), cell.version());
		}
	}

	public static SeasonalityCostGridResponse from(SeasonalityCostGrid grid) {
		return new SeasonalityCostGridResponse(
				grid.countries().stream().map(Country::getCode2).toList(),
				grid.rows().stream().map(Row::from).toList());
	}

	private static <V, R> Map<String, R> mapByCountryCode(Map<Country, V> cells, Function<V, R> mapper) {
		return cells.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().getCode2(), entry -> mapper.apply(entry.getValue())));
	}
}
