package eu.dietwise.common.utils;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;

/**
 * The {@code UniComprehensions} try to make it easier to work with chains of {@code Uni.flatMap} operations, especially
 * in the case where an operation needs the results of more than one operation preceding it. The name is inspired from
 * <a href="https://docs.scala-lang.org/tour/for-comprehensions.html">Scala's for comprehensions</a>.
 * <p>
 * There are two flavors of each function: {@code forc} applies a chain of {@code Uni.flatMap} operations, while
 * {@code forcm} applies a chain of {@code Uni.flatMap} operations, followed by a single {@code Uni.map} operation.
 * </p>
 */
public interface UniComprehensions {
	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R>> mapper1
	) {
		return init.flatMap(mapper1);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R> Uni<R> forc(
			Uni<R1> init,
			Supplier<Uni<? extends R>> mapper1
	) {
		return init.chain(mapper1);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R>> mapper2
	) {
		return init.flatMap(mapper1).flatMap(mapper2);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R>> mapper2
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2)));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2))).map(finalMapper);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).map(r3 -> finalMapper.apply(r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).map(r3 -> finalMapper.apply(r1, r2, r3))));
	}

	@FunctionalInterface
	interface Function3<T1, T2, T3, R> {
		R apply(T1 t1, T2 t2, T3 t3);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(mapper1).flatMap(mapper2).flatMap(mapper3);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2))).flatMap(mapper3);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(mapper1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r2, r3)));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r1, r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3))));
	}
}
