package eu.dietwise.services.v1.extraction.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.extraction.RecipeExtractionAiFacade;
import eu.dietwise.services.v1.extraction.RecipeExtractionAiSelector;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

@ApplicationScoped
public class RecipeExtractionAiFacadeImpl implements RecipeExtractionAiFacade {
	private final RecipeExtractionAiSelector aiSelector;

	public RecipeExtractionAiFacadeImpl(RecipeExtractionAiSelector aiSelector) {
		this.aiSelector = aiSelector;
	}

	@Override
	public Uni<String> extractRecipeFromMarkdown(RecipeLanguage lang, String markdown) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> aiSelector.extractRecipeFromMarkdown(lang, markdown))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}
}
