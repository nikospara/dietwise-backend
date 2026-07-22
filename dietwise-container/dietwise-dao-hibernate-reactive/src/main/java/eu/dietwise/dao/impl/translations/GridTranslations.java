package eu.dietwise.dao.impl.translations;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.RecipeLanguage;
import jakarta.persistence.Tuple;

/**
 * Builds the per-entity, per-language {@link TranslationLangs} completeness the backoffice grids show, from projected
 * translation rows. Each row is a tuple of {@code (entityId, lang, field0, field1, …)}; a language's effective
 * translation is its Working Copy row when one exists (a Working Copy row always differs from master, so its presence is
 * the pending change), otherwise its published master row. Shared by every translatable entity's grid query so the
 * completeness rule lives in one place.
 */
public final class GridTranslations {

	private GridTranslations() {
	}

	public static Map<UUID, TranslationLangs> classify(List<Tuple> masterRows, List<Tuple> workingCopyRows, int fieldCount) {
		Map<UUID, Map<RecipeLanguage, List<String>>> master = fieldsById(masterRows, fieldCount);
		Map<UUID, Map<RecipeLanguage, List<String>>> workingCopy = fieldsById(workingCopyRows, fieldCount);
		Set<UUID> ids = new HashSet<>(master.keySet());
		ids.addAll(workingCopy.keySet());
		Map<UUID, TranslationLangs> result = new HashMap<>();
		for (UUID id : ids) {
			Map<RecipeLanguage, List<String>> m = master.getOrDefault(id, Map.of());
			Map<RecipeLanguage, List<String>> wc = workingCopy.getOrDefault(id, Map.of());
			result.put(id, TranslationLangs.classify(m, wc, wc.keySet()));
		}
		return result;
	}

	private static Map<UUID, Map<RecipeLanguage, List<String>>> fieldsById(List<Tuple> rows, int fieldCount) {
		Map<UUID, Map<RecipeLanguage, List<String>>> byId = new HashMap<>();
		for (Tuple row : rows) {
			List<String> fields = new ArrayList<>(fieldCount);
			for (int i = 0; i < fieldCount; i++) {
				fields.add(row.get(2 + i, String.class));
			}
			byId.computeIfAbsent(row.get(0, UUID.class), _ -> new EnumMap<>(RecipeLanguage.class))
					.put(row.get(1, RecipeLanguage.class), fields);
		}
		return byId;
	}
}
