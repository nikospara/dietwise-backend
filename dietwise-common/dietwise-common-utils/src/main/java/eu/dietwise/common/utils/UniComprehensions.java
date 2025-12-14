package eu.dietwise.common.utils;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;

public interface UniComprehensions {
	static <R1, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R>> mapper1
	) {
		return init.flatMap(mapper1);
	}

	static <R1, R> Uni<R> forc(
			Uni<R1> init,
			Supplier<Uni<? extends R>> mapper1
	) {
		return init.chain(mapper1);
	}

	static <R1, R2, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R>> mapper2
	) {
		return init.flatMap(mapper1).flatMap(mapper2);
	}

	static <R1, R2, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R>> mapper2
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2)));
	}

	@FunctionalInterface
	interface Function3<T1, T2, T3, R> {
		R apply(T1 t1, T2 t2, T3 t3);
	}

	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(mapper1).flatMap(mapper2).flatMap(mapper3);
	}

	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2))).flatMap(mapper3);
	}

	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(mapper1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r2, r3)));
	}

	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r1, r2, r3))));
	}

	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r2, r3))));
	}

	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3))));
	}
}
