package eu.dietwise.v1.types;

import org.immutables.value.Value;

/**
 * Simple seasonality information about an ingredient. It represents the period in a year in which the ingredient is
 * <em>in season</em>. Both ends are inclusive, so {@code from=8, to=10} means that the ingredient is in season from
 * August to October.
 */
@Value.Immutable
public interface Seasonality {
	int getMonthFrom();

	int getMonthTo();
}
