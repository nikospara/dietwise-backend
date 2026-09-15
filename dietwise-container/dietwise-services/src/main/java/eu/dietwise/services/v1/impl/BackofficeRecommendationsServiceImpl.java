package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.recommendations.MasterOverride;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.BackofficeRecommendationsService;
import eu.dietwise.services.v1.types.StagedRecommendation;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeRecommendationsServiceImpl implements BackofficeRecommendationsService {
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
				_ -> recommendationDao.findMasterOverrides(em),
				(_, _) -> recommendationDao.findTranslationLangs(em),
				this::toStagedRecommendations
		));
	}

	@Override
	public Uni<Long> stageMaster(User user, UUID id, String explanation, String humanFriendlyDisplay, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> recommendationDao.stageMaster(tx, id, explanation, humanFriendlyDisplay, baseVersion));
	}

	@Override
	public Uni<Void> revertMaster(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> recommendationDao.revertMaster(tx, id, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, RecommendationTranslationDetails>> translationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> recommendationDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageTranslation(User user, UUID id, RecipeLanguage lang, String name, String componentForScoring, String explanationForLlm, String humanFriendlyDisplay, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> recommendationDao.stageTranslation(tx, id, lang, name, componentForScoring, explanationForLlm, humanFriendlyDisplay, baseVersion));
	}

	@Override
	public Uni<Void> revertTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> recommendationDao.revertTranslation(tx, id, lang, baseVersion));
	}

	private List<StagedRecommendation> toStagedRecommendations(
			List<BackofficeRecommendation> rows,
			Map<UUID, MasterOverride> overridesById,
			Map<UUID, TranslationLangs> langsById
	) {
		return rows.stream()
				.map(row -> toStagedRecommendation(row, overridesById.get(row.id()), langsById.get(row.id())))
				.toList();
	}

	private static StagedRecommendation toStagedRecommendation(BackofficeRecommendation row, MasterOverride override, TranslationLangs langs) {
		boolean staged = override != null;
		String explanation = staged ? override.explanationForLlm() : row.explanationForLlm();
		String humanFriendlyDisplay = staged ? override.humanFriendlyDisplay() : row.humanFriendlyDisplay();
		boolean explanationChanged = staged && !Objects.equals(override.explanationForLlm(), row.explanationForLlm());
		boolean humanFriendlyDisplayChanged = staged && !Objects.equals(override.humanFriendlyDisplay(), row.humanFriendlyDisplay());
		long version = staged ? override.version() : 0L;
		return new StagedRecommendation(
				row.id(),
				row.name(),
				row.componentForScoring(),
				row.typeOfRecommendation(),
				explanation,
				explanationChanged,
				humanFriendlyDisplay,
				humanFriendlyDisplayChanged,
				version,
				BackofficeTranslations.translationStates(langs));
	}
}
