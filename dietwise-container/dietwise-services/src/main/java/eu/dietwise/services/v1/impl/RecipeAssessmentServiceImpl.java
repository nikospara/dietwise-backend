package eu.dietwise.services.v1.impl;

import static eu.dietwise.services.v1.ai.MarkdownBlockHeadingJoiner.joinHeadingWithFollowingContent;
import static eu.dietwise.services.v1.ai.MarkdownBlockSegmenter.segment;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.renderer.RenderRequest;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.ai.MarkdownBlockSegmenter;
import eu.dietwise.services.v1.ai.RecipeExtractionService;
import eu.dietwise.services.v1.ai.RecipeFilterAiService;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Suggestion;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeAssessmentServiceImpl implements RecipeAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeAssessmentServiceImpl.class);

	private final RecipeFilterAiService filterAiService;
	private final RecipeExtractionService extractionService;
	private final RendererClient rendererClient;

	public RecipeAssessmentServiceImpl(RecipeFilterAiService filterAiService, RecipeExtractionService extractionService, @RestClient RendererClient rendererClient) {
		this.filterAiService = filterAiService;
		this.extractionService = extractionService;
		this.rendererClient = rendererClient;
	}

	@Override
	public Multi<RecipeAssessmentMessage> assessHtmlRecipe(RecipeAssessmentParam param) {
		return assessRecipe(param, this::extractRecipeFromHtml);
	}

	@Override
	public Multi<RecipeAssessmentMessage> assessMarkdownRecipe(RecipeAssessmentParam param) {
		return assessRecipe(param, this::extractRecipeFromMarkdown);
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(RecipeExtractionAndAssessmentParam param) {
		return Multi.createFrom().emitter(emitter -> {
			var renderRequest = new RenderRequest(param.getUrl(), true, true, 30000, param.getViewport().orElse(null), false, true);
			rendererClient.render(renderRequest)
					.flatMap(restRenderResponse -> extractRecipesEitherFromJsonLdOrFromAi(restRenderResponse, param, this::extractRecipeFromMarkdown))
					.invoke(emitRecipeExtractionMessageOrNoRecipesError(emitter))
					.flatMap(this::assessSingleRecipe)
					.invoke(emitter::emit)
					.subscribe().with(x -> emitter.complete(), handleError(emitter));
		});
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrlDummy(RecipeExtractionAndAssessmentParam param) {
		var recipe = makeDummyRecipe();
		var recipeMsg = new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, JSONLD)), "dummy page text");
		double rating = new Random().nextInt(10) / 2.0;
		List<Suggestion> suggestions = List.of(
				ImmutableSuggestion.builder().text("Coming from the server - this is just a dummy, placeholder response").build(),
				ImmutableSuggestion.builder().text("At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat.").build()
		);
		var suggestionsMsg = new SuggestionsRecipeAssessmentMessage(rating, suggestions);
		return Multi.createFrom().<RecipeAssessmentMessage>items(recipeMsg, suggestionsMsg)
				.onItem().call(m -> Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofSeconds(3L)));
	}

	private Recipe makeDummyRecipe() {
		return ImmutableRecipe.builder()
				.text("""
						This is a dummy recipe, just for testing purposes
						Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
						Ed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?
						""")
				.addRecipeIngredients(
						"800 g ground beef",
						"5 slices sandwich bread, 120 g",
						"¼ bunch parsley",
						"5-6 mint leaves",
						"1 tablespoon(s) ketchup",
						"1 egg, medium",
						"1 onion",
						"150g cheddar, grated",
						"100g cream cheese"
				)
				.build();
	}

	private Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipesEitherFromJsonLdOrFromAi(RestResponse<RenderResponse> restRenderResponse, RecipeExtractionAndAssessmentParam param, Function<RecipeAssessmentParam, Uni<Recipe>> extractor) {
		RenderResponse renderResponse = restRenderResponse.getEntity();
		if (renderResponse.jsonLdRecipes() != null && !renderResponse.jsonLdRecipes().isEmpty()) {
			var recipes = renderResponse.jsonLdRecipes().stream().map(r -> new RecipeAndDetectionType(r, JSONLD)).toList();
			return Uni.createFrom().item(new RecipeExtractionRecipeAssessmentMessage(recipes, renderResponse.output()));
		} else {
			return keepOnlyRelevantPageContent(renderResponse.output(), param.getLangCode())
					.map(filteredContent -> ImmutableRecipeAssessmentParam.builder().url(param.getUrl()).langCode(param.getLangCode()).pageContent(filteredContent).build())
					.flatMap(extractor::apply)
					.map(llmResponse -> convertLlmResponseToRecipes(llmResponse, renderResponse.output()));
		}
	}

	private Uni<String> keepOnlyRelevantPageContent(String pageTextAsMarkdown, String langCode) {
		List<MarkdownBlockSegmenter.Block> segmentedContent = segment(pageTextAsMarkdown);
		List<String> blocks = joinHeadingWithFollowingContent(segmentedContent);
		return Multi.createFrom().iterable(blocks).onItem().transformToUniAndConcatenate(this::filterRecipeBlock)
				.filter(block -> !block.isBlank())
				.collect().asList()
				.map(filteredBlocks -> String.join("\n\n", filteredBlocks));
	}

	private Uni<String> filterRecipeBlock(String block) {
		return Uni.createFrom().item(() -> filterAiService.filterRecipeBlock(block))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(result -> result == null ? "" : result.trim().toUpperCase()) // sanitize
				.map(result -> "KEEP".equals(result) ? block : "");
	}

	private RecipeExtractionRecipeAssessmentMessage convertLlmResponseToRecipes(Recipe recipe, String pageText) {
		return new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, LLM_FROM_TEXT)), pageText);
	}

	private Consumer<RecipeExtractionRecipeAssessmentMessage> emitRecipeExtractionMessageOrNoRecipesError(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipeExtractionRecipeAssessmentMessage -> {
			if (recipeExtractionRecipeAssessmentMessage.recipes() == null || recipeExtractionRecipeAssessmentMessage.recipes().isEmpty()) {
				throw new NoRecipesDetectedException();
			} else {
				emitter.emit(recipeExtractionRecipeAssessmentMessage);
			}
		};
	}

	private Uni<SuggestionsRecipeAssessmentMessage> assessSingleRecipe(RecipeExtractionRecipeAssessmentMessage message) {
		if (message.recipes().size() != 1) {
			// this signals handleError to emit MoreThanOneRecipesAssessmentMessage
			return Uni.createFrom().failure(new MoreThanOneRecipesDetectedException(message.recipes().size()));
		} else {
			// TODO Call the actual assessment service here
			return Uni.createFrom().item(makeDummySuggestionsRecipeAssessmentMessage())
					.onItem().delayIt().by(Duration.ofSeconds(2L)); // DUMMY for initial testing/demos
		}
	}

	private Multi<RecipeAssessmentMessage> assessRecipe(RecipeAssessmentParam param, Function<RecipeAssessmentParam, Uni<Recipe>> extractor) {
		return Multi.createFrom().emitter(emitter ->
				keepOnlyRelevantPageContent(param.getPageContent(), param.getLangCode())
						.map(filteredContent -> ImmutableRecipeAssessmentParam.builder().url(param.getUrl()).langCode(param.getLangCode()).pageContent(filteredContent).build())
						.flatMap(extractor::apply)
						.invoke(emitRecipeExtractionRecipeAssessmentMessage(emitter))
						.onItem().delayIt().by(Duration.ofSeconds(2L)) // DUMMY for initial testing/demos
						.invoke(emitSuggestionsRecipeAssessmentMessage(emitter))
						.subscribe().with(x -> emitter.complete(), handleError(emitter)));
	}

	private Uni<Recipe> extractRecipeFromHtml(RecipeAssessmentParam param) {
		return extractionService.extractRecipeFromHtml(param.getPageContent());
	}

	private Uni<Recipe> extractRecipeFromMarkdown(RecipeAssessmentParam param) {
		return extractionService.extractRecipeFromMarkdown(param.getPageContent());
	}

	private Consumer<Recipe> emitRecipeExtractionRecipeAssessmentMessage(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipe -> emitter.emit(new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, LLM_FROM_TEXT)), ""));
	}

	private Consumer<Recipe> emitSuggestionsRecipeAssessmentMessage(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipe -> {
			emitter.emit(makeDummySuggestionsRecipeAssessmentMessage());
		};
	}

	private SuggestionsRecipeAssessmentMessage makeDummySuggestionsRecipeAssessmentMessage() {
		double rating = new Random().nextInt(10) / 2.0;
		List<Suggestion> suggestions = List.of(
				ImmutableSuggestion.builder().text("Coming from the server - this is just a dummy, placeholder response").build(),
//				ImmutableSuggestion.builder().text("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.").build(),
//				ImmutableSuggestion.builder().text("Ed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?").build(),
				ImmutableSuggestion.builder().text("At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat.").build()
		);
//		throw new RuntimeException("Testing exception");
		return new SuggestionsRecipeAssessmentMessage(rating, suggestions);
	}

	private Consumer<Throwable> handleError(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return error -> {
			if (error instanceof NoRecipesDetectedException) {
				emitter.emit(new RecipeAssessmentErrorMessage(List.of("No recipes detected on the page")));
			} else if (error instanceof MoreThanOneRecipesDetectedException mto) {
				emitter.emit(new MoreThanOneRecipesAssessmentMessage(mto.getNumberOfRecipes()));
			} else {
				emitter.emit(new RecipeAssessmentErrorMessage(List.of("The server failed to assess the recipe")));
			}
			emitter.complete();
		};
	}

	private Function<ClientWebApplicationException, Multi<RecipeAssessmentMessage>> handleHtmlExtractionError(RecipeExtractionAndAssessmentParam param) {
		return e -> {
			LOG.error("Could not read the page at {}", param.getUrl(), e);
			return Multi.createFrom().item(new RecipeAssessmentErrorMessage(List.of("Could not read the page")));
		};
	}
}
