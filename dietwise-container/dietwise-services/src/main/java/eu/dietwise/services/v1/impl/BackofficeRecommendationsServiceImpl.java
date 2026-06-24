package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.BackofficeRecommendationsService;
import eu.dietwise.services.v1.types.StagedRecommendation;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeRecommendationsServiceImpl implements BackofficeRecommendationsService {
	private static final List<RecipeLanguage> TRANSLATABLE_LANGUAGES =
			Arrays.stream(RecipeLanguage.values()).filter(lang -> lang != RecipeLanguage.EN).toList();

	private final RecommendationDao recommendationDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeRecommendationsServiceImpl(
			RecommendationDao recommendationDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.recommendationDao = recommendationDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<List<StagedRecommendation>> listRecommendations(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				recommendationDao.listForBackoffice(em),
				_ -> recommendationDao.findTranslationLangs(em),
				this::toStagedRecommendations
		));
	}

	private List<StagedRecommendation> toStagedRecommendations(List<BackofficeRecommendation> rows, Map<java.util.UUID, TranslationLangs> langsById) {
		return rows.stream().map(row -> toStagedRecommendation(row, langsById.get(row.id()))).toList();
	}

	private static StagedRecommendation toStagedRecommendation(BackofficeRecommendation row, TranslationLangs langs) {
		return new StagedRecommendation(
				row.id(),
				row.name(),
				row.componentForScoring(),
				row.weight(),
				row.explanationForLlm(),
				toTranslationStates(langs));
	}

	private static Map<RecipeLanguage, TranslationState> toTranslationStates(TranslationLangs langs) {
		Set<RecipeLanguage> present = langs == null ? Set.of() : langs.present();
		Set<RecipeLanguage> staged = langs == null ? Set.of() : langs.staged();
		Map<RecipeLanguage, TranslationState> states = new EnumMap<>(RecipeLanguage.class);
		for (RecipeLanguage lang : TRANSLATABLE_LANGUAGES) {
			states.put(lang, staged.contains(lang)
					? TranslationState.STAGED
					: present.contains(lang) ? TranslationState.PRESENT : TranslationState.MISSING);
		}
		return states;
	}
}
