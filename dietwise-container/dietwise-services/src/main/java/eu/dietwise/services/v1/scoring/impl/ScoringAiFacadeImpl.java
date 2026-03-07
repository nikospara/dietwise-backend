package eu.dietwise.services.v1.scoring.impl;

import java.util.Collections;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiService;
import eu.dietwise.services.v1.scoring.ScoringAiFacade;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ScoringAiFacadeImpl implements ScoringAiFacade {
	private static final Logger LOG = LoggerFactory.getLogger(ScoringAiFacadeImpl.class);

	private final IngredientMatchInRecommendationsAiService ingredientMatchInRecommendationsAiService;

	public ScoringAiFacadeImpl(IngredientMatchInRecommendationsAiService ingredientMatchInRecommendationsAiService) {
		this.ingredientMatchInRecommendationsAiService = ingredientMatchInRecommendationsAiService;
	}

	@Override
	public Uni<Set<String>> matchIngredientsWithRecommendations(String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe) {
		Context callerContext = Vertx.currentContext();
		Uni<Set<String>> resultUni = Uni.createFrom().item(() -> ingredientMatchInRecommendationsAiService.matchIngredientsWithRecommendations(
						availableRecommendationsAsMarkdownList, ingredientNameInRecipe))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(recommendationsFromAi -> postProcessRecommendationsFromAi(ingredientNameInRecipe, recommendationsFromAi));
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	private Set<String> postProcessRecommendationsFromAi(String ingredientNameInRecipe, String recommendationsFromAi) {
		LOG.debug("matchIngredientsWithRecommendations responded for ingredient <{}>: {}", ingredientNameInRecipe, recommendationsFromAi);
		if (recommendationsFromAi == null) return Collections.emptySet();
		recommendationsFromAi = recommendationsFromAi.trim();
		return Set.of(recommendationsFromAi.split("\\r?\\n"));
	}
}
