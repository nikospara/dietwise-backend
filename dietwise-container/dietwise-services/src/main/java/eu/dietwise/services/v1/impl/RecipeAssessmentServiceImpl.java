package eu.dietwise.services.v1.impl;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.ai.RecipeAssessmentAiService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.Suggestion;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeAssessmentServiceImpl implements RecipeAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeAssessmentServiceImpl.class);

	private final RecipeAssessmentAiService aiService;

	public RecipeAssessmentServiceImpl(RecipeAssessmentAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public Multi<RecipeAssessmentMessage> assessHtmlRecipe(RecipeAssessmentParam param) {
		return Multi.createFrom().emitter(emitter -> {
			extractRecipeFromHtml(param)
					.map(this::convertLlmResponseToRecipe)
					.invoke(emitRecipeExtractionRecipeAssessmentMessage(emitter))
					.onItem().delayIt().by(Duration.ofSeconds(2L))
					.invoke(emitSuggestionsRecipeAssessmentMessage(emitter))
					.subscribe().with(x -> emitter.complete(), handleError(emitter));
		});
	}

	private Uni<String> extractRecipeFromHtml(RecipeAssessmentParam param) {
		return Uni.createFrom().item(() -> aiService.extractRecipeFromHtml(param.getPageContent()))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
	}

	private Recipe convertLlmResponseToRecipe(String text) {
		return ImmutableRecipe.builder().text(text).build();
	}

	private Consumer<Recipe> emitRecipeExtractionRecipeAssessmentMessage(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipe -> emitter.emit(new RecipeExtractionRecipeAssessmentMessage(List.of(recipe)));
	}

	private Consumer<Recipe> emitSuggestionsRecipeAssessmentMessage(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipe -> {
			double rating = new Random().nextInt(10) / 2.0;
			List<Suggestion> suggestions = List.of(
					ImmutableSuggestion.builder().text("Coming from the server").build(),
					ImmutableSuggestion.builder().text("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.").build(),
					ImmutableSuggestion.builder().text("Ed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?").build(),
					ImmutableSuggestion.builder().text("At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat.").build()
			);
//			throw new RuntimeException("Testing exception");
			emitter.emit(new SuggestionsRecipeAssessmentMessage(rating, suggestions));
		};
	}

	private Consumer<Throwable> handleError(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return error -> {
			emitter.emit(new RecipeAssessmentErrorMessage(List.of("The server failed to assess the recipe")));
			emitter.complete();
		};
	}
}
