package eu.dietwise.common.utils;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.smallrye.mutiny.tuples.Functions.Function3;

public interface MultiComprehensions {
	/**
	 * Create a {@code Multi} from a series of {@code Uni}s, executed sequentially, each possibly emitting a valueto the
	 * {@code Multi}. The {@code Multi} completes after the last {@code Uni} completes and the {@code errorHandler} is
	 * given the opportunity to handle any errors and emit a final message, by default without failing the {@code Multi}.
	 * <p>
	 * About error handling: Any failure thrown by the {@code Uni} chain is caught by a subscription error handler.
	 * By default, this error handler will complete the {@code Multi} without errors. If you actually want the
	 * {@code Multi} to complete with an error, just call {@code emitter.fail(t)} in the {@code errorHandler}.
	 * </p>
	 */
	static <M, R1, R2, R3, R4> Multi<M> emitInSequence(
			Uni<R1> init,
			BiFunction<MultiEmitter<? super M>, ? super R1, Uni<? extends R2>> mapper1,
			BiFunction<MultiEmitter<? super M>, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<MultiEmitter<? super M>, ? super R3, Uni<? extends R4>> mapper3,
			BiConsumer<MultiEmitter<? super M>, ? super Throwable> errorHandler
	) {
		return Multi.createFrom().emitter(emitter -> forc(
				init,
				r1 -> mapper1.apply(emitter, r1),
				r2 -> mapper2.apply(emitter, r2),
				r3 -> mapper3.apply(emitter, r3)
		).subscribe().with(_ -> emitter.complete(), t -> {
			errorHandler.accept(emitter, t);
			emitter.complete();
		}));
	}

	/**
	 * Create a {@code Multi} from a series of {@code Uni}s, executed sequentially, each possibly emitting a valueto the
	 * {@code Multi}. The {@code Multi} completes after the last {@code Uni} completes and the {@code errorHandler} is
	 * given the opportunity to handle any errors and emit a final message, by default without failing the {@code Multi}.
	 * <p>
	 * About error handling: Any failure thrown by the {@code Uni} chain is caught by a subscription error handler.
	 * By default, this error handler will complete the {@code Multi} without errors. If you actually want the
	 * {@code Multi} to complete with an error, just call {@code emitter.fail(t)} in the {@code errorHandler}.
	 * </p>
	 */
	static <M, R1, R2, R3, R4, R5> Multi<M> emitInSequence(
			Function<MultiEmitter<? super M>, Uni<? extends R1>> init,
			BiFunction<MultiEmitter<? super M>, ? super R1, Uni<? extends R2>> mapper1,
			BiFunction<MultiEmitter<? super M>, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<MultiEmitter<? super M>, ? super R3, Uni<? extends R4>> mapper3,
			BiFunction<MultiEmitter<? super M>, ? super R4, Uni<? extends R5>> mapper4,
			BiConsumer<MultiEmitter<? super M>, ? super Throwable> errorHandler
	) {
		return Multi.createFrom().emitter(emitter -> forc(
				init.apply(emitter),
				r1 -> mapper1.apply(emitter, r1),
				r2 -> mapper2.apply(emitter, r2),
				r3 -> mapper3.apply(emitter, r3),
				r4 -> mapper4.apply(emitter, r4)
		).subscribe().with(_ -> emitter.complete(), t -> {
			errorHandler.accept(emitter, t);
			emitter.complete();
		}));
	}

	/**
	 * Create a {@code Multi} from a series of {@code Uni}s, executed sequentially, each possibly emitting a valueto the
	 * {@code Multi}. The {@code Multi} completes after the last {@code Uni} completes and the {@code errorHandler} is
	 * given the opportunity to handle any errors and emit a final message, by default without failing the {@code Multi}.
	 * <p>
	 * About error handling: Any failure thrown by the {@code Uni} chain is caught by a subscription error handler.
	 * By default, this error handler will complete the {@code Multi} without errors. If you actually want the
	 * {@code Multi} to complete with an error, just call {@code emitter.fail(t)} in the {@code errorHandler}.
	 * </p>
	 */
	static <M, R1, R2, R3, R4, R5> Multi<M> emitInSequence(
			Uni<R1> init,
			BiFunction<MultiEmitter<? super M>, ? super R1, Uni<? extends R2>> mapper1,
			BiFunction<MultiEmitter<? super M>, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<MultiEmitter<? super M>, ? super R3, Uni<? extends R4>> mapper3,
			BiFunction<MultiEmitter<? super M>, ? super R4, Uni<? extends R5>> mapper4,
			BiConsumer<MultiEmitter<? super M>, ? super Throwable> errorHandler
	) {
		return Multi.createFrom().emitter(emitter -> forc(
				init,
				r1 -> mapper1.apply(emitter, r1),
				r2 -> mapper2.apply(emitter, r2),
				r3 -> mapper3.apply(emitter, r3),
				r4 -> mapper4.apply(emitter, r4)
		).subscribe().with(_ -> emitter.complete(), t -> {
			errorHandler.accept(emitter, t);
			emitter.complete();
		}));
	}

	/**
	 * Create a {@code Multi} from a series of {@code Uni}s, executed sequentially, each possibly emitting a valueto the
	 * {@code Multi}. The {@code Multi} completes after the last {@code Uni} completes and the {@code errorHandler} is
	 * given the opportunity to handle any errors and emit a final message, by default without failing the {@code Multi}.
	 * <p>
	 * About error handling: Any failure thrown by the {@code Uni} chain is caught by a subscription error handler.
	 * By default, this error handler will complete the {@code Multi} without errors. If you actually want the
	 * {@code Multi} to complete with an error, just call {@code emitter.fail(t)} in the {@code errorHandler}.
	 * </p>
	 */
	static <M, R1, R2, R3, R4, R5> Multi<M> emitInSequence(
			Uni<R1> init,
			BiFunction<MultiEmitter<? super M>, ? super R1, Uni<? extends R2>> mapper1,
			BiFunction<MultiEmitter<? super M>, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<MultiEmitter<? super M>, ? super R3, Uni<? extends R4>> mapper3,
			Function3<MultiEmitter<? super M>, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			BiConsumer<MultiEmitter<? super M>, ? super Throwable> errorHandler
	) {
		return Multi.createFrom().emitter(emitter -> forc(
				init,
				r1 -> mapper1.apply(emitter, r1),
				r2 -> mapper2.apply(emitter, r2),
				r3 -> mapper3.apply(emitter, r3),
				(r3, r4) -> mapper4.apply(emitter, r3, r4)
		).subscribe().with(_ -> emitter.complete(), t -> {
			errorHandler.accept(emitter, t);
			emitter.complete();
		}));
	}

	/**
	 * Create a {@code Multi} from a series of {@code Uni}s, executed sequentially, each possibly emitting a valueto the
	 * {@code Multi}. The {@code Multi} completes after the last {@code Uni} completes and the {@code errorHandler} is
	 * given the opportunity to handle any errors and emit a final message, by default without failing the {@code Multi}.
	 * <p>
	 * About error handling: Any failure thrown by the {@code Uni} chain is caught by a subscription error handler.
	 * By default, this error handler will complete the {@code Multi} without errors. If you actually want the
	 * {@code Multi} to complete with an error, just call {@code emitter.fail(t)} in the {@code errorHandler}.
	 * </p>
	 */
	static <M, R1, R2, R3, R4, R5, R6> Multi<M> emitInSequence(
			Uni<R1> init,
			BiFunction<MultiEmitter<? super M>, ? super R1, Uni<? extends R2>> mapper1,
			BiFunction<MultiEmitter<? super M>, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<MultiEmitter<? super M>, ? super R3, Uni<? extends R4>> mapper3,
			BiFunction<MultiEmitter<? super M>, ? super R4, Uni<? extends R5>> mapper4,
			Function3<MultiEmitter<? super M>, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			BiConsumer<MultiEmitter<? super M>, ? super Throwable> errorHandler
	) {
		return Multi.createFrom().emitter(emitter -> forc(
				init,
				r1 -> mapper1.apply(emitter, r1),
				r2 -> mapper2.apply(emitter, r2),
				r3 -> mapper3.apply(emitter, r3),
				r4 -> mapper4.apply(emitter, r4),
				(r4, r5) -> mapper5.apply(emitter, r4, r5)
		).subscribe().with(_ -> emitter.complete(), t -> {
			errorHandler.accept(emitter, t);
			emitter.complete();
		}));
	}
}
