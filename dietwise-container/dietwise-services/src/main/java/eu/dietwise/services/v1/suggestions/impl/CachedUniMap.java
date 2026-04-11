package eu.dietwise.services.v1.suggestions.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;

final class CachedUniMap<K, V> {
	private final ConcurrentHashMap<K, V> values = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<K, Uni<V>> inFlight = new ConcurrentHashMap<>();

	Uni<V> getOrLoad(K key, Supplier<Uni<V>> loader) {
		var cached = values.get(key);
		if (cached != null) {
			return Uni.createFrom().item(cached);
		}

		return inFlight.computeIfAbsent(key, _ -> loader.get()
				.onItem().invoke(loaded -> {
					values.put(key, loaded);
					inFlight.remove(key);
				})
				.onFailure().invoke(() -> inFlight.remove(key))
				.memoize().indefinitely());
	}
}
