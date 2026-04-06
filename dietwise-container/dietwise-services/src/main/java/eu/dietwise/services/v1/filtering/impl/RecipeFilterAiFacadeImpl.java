package eu.dietwise.services.v1.filtering.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.filtering.RecipeFilterAiFacade;
import eu.dietwise.services.v1.filtering.RecipeFilterAiService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

@ApplicationScoped
public class RecipeFilterAiFacadeImpl implements RecipeFilterAiFacade {
	private final RecipeFilterAiService aiService;

	public RecipeFilterAiFacadeImpl(RecipeFilterAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public Uni<String> filterRecipeBlock(String block) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> aiService.filterRecipeBlock(block))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}
}
