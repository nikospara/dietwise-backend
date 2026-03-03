package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import org.junit.jupiter.api.Test;

class CachedUniValueTest {
	@Test
	void cachesLoadedValueAfterFirstSuccessfulLoad() {
		var sut = new CachedUniValue<String>();
		var loaderCalls = new AtomicInteger();

		Supplier<Uni<String>> loader = () -> {
			loaderCalls.incrementAndGet();
			return Uni.createFrom().item("loaded");
		};

		var first = sut.getOrLoad(loader).await().indefinitely();
		var second = sut.getOrLoad(loader).await().indefinitely();

		assertThat(first).isEqualTo("loaded");
		assertThat(second).isEqualTo("loaded");
		assertThat(loaderCalls).hasValue(1);
	}

	@Test
	void reusesInFlightLoadAcrossConcurrentCallers() throws Exception {
		var sut = new CachedUniValue<String>();
		var loaderCalls = new AtomicInteger();
		var emitterRef = new AtomicReference<UniEmitter<? super String>>();

		Supplier<Uni<String>> loader = () -> {
			loaderCalls.incrementAndGet();
			return Uni.createFrom().<String>emitter(emitter -> emitterRef.set(emitter));
		};

		var first = sut.getOrLoad(loader);
		var second = sut.getOrLoad(loader);

		assertThat(second).isSameAs(first);
		assertThat(loaderCalls).hasValue(1);

		CompletableFuture<String> firstResult = new CompletableFuture<>();
		CompletableFuture<String> secondResult = new CompletableFuture<>();
		first.subscribe().with(firstResult::complete, firstResult::completeExceptionally);
		second.subscribe().with(secondResult::complete, secondResult::completeExceptionally);

		emitterRef.get().complete("loaded");

		assertThat(firstResult.get(1, TimeUnit.SECONDS)).isEqualTo("loaded");
		assertThat(secondResult.get(1, TimeUnit.SECONDS)).isEqualTo("loaded");
	}

	@Test
	void retriesLoadAfterPreviousFailure() {
		var sut = new CachedUniValue<String>();
		var loaderCalls = new AtomicInteger();

		Supplier<Uni<String>> loader = () -> {
			int invocation = loaderCalls.incrementAndGet();
			if (invocation == 1) {
				return Uni.createFrom().failure(new IllegalStateException("boom"));
			}
			return Uni.createFrom().item("loaded");
		};

		assertThatThrownBy(() -> sut.getOrLoad(loader).await().indefinitely())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");

		assertThat(sut.getOrLoad(loader).await().indefinitely()).isEqualTo("loaded");
		assertThat(sut.getOrLoad(loader).await().indefinitely()).isEqualTo("loaded");
		assertThat(loaderCalls).hasValue(2);
	}
}
