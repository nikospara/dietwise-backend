package eu.dietwise.services.v1.filtering.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.filtering.RecipeFilterAiFacade;
import eu.dietwise.services.v1.filtering.RecipeFilterAiSelector;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

@ApplicationScoped
public class RecipeFilterAiFacadeImpl implements RecipeFilterAiFacade {
	private final RecipeFilterAiSelector aiSelector;

	public RecipeFilterAiFacadeImpl(RecipeFilterAiSelector aiSelector) {
		this.aiSelector = aiSelector;
	}

	@Override
	public Uni<String> filterRecipeBlock(RecipeLanguage lang, String block) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> aiSelector.filterRecipeBlock(lang, block))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}
}
