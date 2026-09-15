package eu.dietwise.services.nondomain;

import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;

/**
 * Holds one lazily loaded value, loading it at most once: concurrent callers share the load in flight and every caller
 * after it completes gets the loaded value. A load that fails is not kept, so the next caller tries again.
 */
public final class CachedUniValue<T> {
	private volatile T value;
	private volatile Uni<T> inFlight;

	public Uni<T> getOrLoad(Supplier<Uni<T>> loader) {
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
