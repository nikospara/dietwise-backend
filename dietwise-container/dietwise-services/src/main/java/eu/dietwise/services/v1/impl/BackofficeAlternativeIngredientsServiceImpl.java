package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.EntityInUseException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.suggestions.BackofficeAlternativeIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.BackofficeAlternativeIngredientsService;
import eu.dietwise.services.v1.types.AlternativeIngredientRecommendationGrid;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedAlternativeIngredient;
import eu.dietwise.v1.types.RecommendationWeight;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeAlternativeIngredientsServiceImpl implements BackofficeAlternativeIngredientsService {
	private final AlternativeIngredientDao alternativeIngredientDao;
	private final RecommendationDao recommendationDao;
	private final SuggestionTemplateDao suggestionTemplateDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeAlternativeIngredientsServiceImpl(
			AlternativeIngredientDao alternativeIngredientDao,
			RecommendationDao recommendationDao,
			SuggestionTemplateDao suggestionTemplateDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.recommendationDao = recommendationDao;
		this.suggestionTemplateDao = suggestionTemplateDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<AlternativeIngredientRecommendationGrid> recommendationGrid(User user) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				recommendationDao.listForBackoffice(em),
				_ -> alternativeIngredientDao.listForBackoffice(em),
				_ -> alternativeIngredientDao.findTranslationLangs(em),
				_ -> alternativeIngredientDao.findMasterRecommendationLinks(em),
				_ -> alternativeIngredientDao.findStagedRecommendationLinks(em),
				this::toGrid
		));
	}

	@Override
	public Uni<Void> toggleRecommendation(User user, UUID alternativeIngredientId, UUID recommendationId, boolean present) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.toggleRecommendationLink(tx, alternativeIngredientId, recommendationId, present));
	}

	@Override
	public Uni<Void> discardAlternativeIngredient(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> forc(
				alternativeIngredientDao.findEditableById(tx, id),
				details -> failIfAlternativeIngredientIsPublished(details, id),
				_ -> suggestionTemplateDao.countTemplatesByAlternative(tx, id),
				count -> failIfAlternativeIngredientIsReferenced(count, id),
				_ -> alternativeIngredientDao.discardAlternativeIngredient(tx, id)
		));
	}

	private static Uni<ReferenceDetails> failIfAlternativeIngredientIsPublished(ReferenceDetails details, UUID id) {
		return details.published()
				? Uni.createFrom().failure(new EntityInUseException("Alternative Ingredient is published and cannot be discarded: " + id))
				: Uni.createFrom().item(details);
	}

	private static Uni<Void> failIfAlternativeIngredientIsReferenced(long count, UUID id) {
		return count > 0
				? Uni.createFrom().failure(new EntityInUseException("Alternative Ingredient cannot be discarded because it is referenced by " + count + " Suggestion Template(s): " + id))
				: Uni.createFrom().voidItem();
	}

	private AlternativeIngredientRecommendationGrid toGrid(
			List<BackofficeRecommendation> recommendations,
			List<BackofficeAlternativeIngredient> ingredients,
			Map<UUID, TranslationLangs> langsById,
			Map<UUID, Set<UUID>> masterLinks,
			Map<UUID, Map<UUID, Boolean>> stagedLinks
	) {
		List<RecommendationColumn> columns = recommendations.stream()
				.filter(recommendation -> recommendation.weight() == RecommendationWeight.ENCOURAGED)
				.map(recommendation -> new RecommendationColumn(recommendation.id(), recommendation.componentForScoring()))
				.toList();
		Set<UUID> columnIds = columns.stream().map(RecommendationColumn::id).collect(Collectors.toSet());
		List<StagedAlternativeIngredient> rows = ingredients.stream()
				.map(ingredient -> toRow(ingredient, columnIds, langsById.get(ingredient.id()), masterLinks.get(ingredient.id()), stagedLinks.get(ingredient.id())))
				.toList();
		return new AlternativeIngredientRecommendationGrid(columns, rows);
	}

	private static StagedAlternativeIngredient toRow(
			BackofficeAlternativeIngredient ingredient,
			Set<UUID> columnIds,
			TranslationLangs langs,
			Set<UUID> masterLinks,
			Map<UUID, Boolean> stagedLinks
	) {
		return new StagedAlternativeIngredient(
				ingredient.id(),
				ingredient.name(),
				ingredient.published(),
				ingredient.version(),
				BackofficeTranslations.translationStates(langs),
				retainColumns(masterLinks, columnIds),
				retainColumns(stagedLinks == null ? null : stagedLinks.keySet(), columnIds));
	}

	private static Set<UUID> retainColumns(Set<UUID> ids, Set<UUID> columnIds) {
		if (ids == null) {
			return Set.of();
		}
		return ids.stream().filter(columnIds::contains).collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
