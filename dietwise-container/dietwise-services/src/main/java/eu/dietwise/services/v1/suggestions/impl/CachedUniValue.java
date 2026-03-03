package eu.dietwise.services.v1.suggestions.impl;

import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;

final class CachedUniValue<T> {
	private volatile T value;
	private volatile Uni<T> inFlight;

	Uni<T> getOrLoad(Supplier<Uni<T>> loader) {
		var cached = value;
		if (cached != null) {
			return Uni.createFrom().item(cached);
		}

		var currentLoad = inFlight;
		if (currentLoad != null) {
			return currentLoad;
		}

		synchronized (this) {
			cached = value;
			if (cached != null) {
				return Uni.createFrom().item(cached);
			}

			currentLoad = inFlight;
			if (currentLoad == null) {
				currentLoad = loader.get()
						.onItem().invoke(loaded -> {
							value = loaded;
							inFlight = null;
						})
						.onFailure().invoke(() -> inFlight = null)
						.memoize().indefinitely();
				inFlight = currentLoad;
			}
		}

		return currentLoad;
	}
}
