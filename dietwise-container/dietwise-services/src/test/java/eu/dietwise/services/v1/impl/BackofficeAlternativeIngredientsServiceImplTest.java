package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.EntityInUseException;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.suggestions.BackofficeAlternativeIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.types.AlternativeIngredientRecommendationGrid;
import eu.dietwise.services.v1.types.RecommendationColumn;
import eu.dietwise.services.v1.types.StagedAlternativeIngredient;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RecommendationWeight;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackofficeAlternativeIngredientsServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);

	private static final UUID REC_LEGUMES_ID = UUID.fromString("b1b2b3b4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID REC_WHOLE_GRAINS_ID = UUID.fromString("b1b2b3b4-0002-4f5a-8b9c-0d1e2f3a0002");
	private static final UUID REC_PROCESSED_MEAT_ID = UUID.fromString("b1b2b3b4-0003-4f5a-8b9c-0d1e2f3a0003");
	private static final UUID AI_LENTILS_ID = UUID.fromString("c1c2c3c4-0001-4f5a-8b9c-0d1e2f3a0001");
	private static final UUID AI_TOFU_ID = UUID.fromString("c1c2c3c4-0002-4f5a-8b9c-0d1e2f3a0002");

	@Mock
	private AlternativeIngredientDao alternativeIngredientDao;

	@Mock
	private RecommendationDao recommendationDao;

	@Mock
	private SuggestionTemplateDao suggestionTemplateDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void recommendationGridKeepsOnlyEncouragedColumnsAndOverlaysLinksForAnAdmin() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(REC_LEGUMES_ID, "Increase legumes", "legumes", RecommendationWeight.ENCOURAGED, null),
				new BackofficeRecommendation(REC_PROCESSED_MEAT_ID, "Decrease processed meat", "processed meat", RecommendationWeight.LIMITED, null),
				new BackofficeRecommendation(REC_WHOLE_GRAINS_ID, "Increase whole grains", "whole grains", RecommendationWeight.ENCOURAGED, null))));
		when(alternativeIngredientDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeAlternativeIngredient(AI_LENTILS_ID, "Lentils", true, 0L),
				new BackofficeAlternativeIngredient(AI_TOFU_ID, "Tofu", false, 2L))));
		when(alternativeIngredientDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				AI_LENTILS_ID, new TranslationLangs(EnumSet.of(RecipeLanguage.EL), EnumSet.of(RecipeLanguage.NL)))));
		when(alternativeIngredientDao.findMasterRecommendationLinks(any())).thenReturn(Uni.createFrom().item(Map.of(
				AI_LENTILS_ID, Set.of(REC_LEGUMES_ID, REC_PROCESSED_MEAT_ID))));
		when(alternativeIngredientDao.findStagedRecommendationLinks(any())).thenReturn(Uni.createFrom().item(Map.of(
				AI_LENTILS_ID, Map.of(REC_WHOLE_GRAINS_ID, true),
				AI_TOFU_ID, Map.of(REC_LEGUMES_ID, true))));

		AlternativeIngredientRecommendationGrid grid = newService().recommendationGrid(adminUser()).await().atMost(AWAIT);

		assertThat(grid.columns()).containsExactly(
				new RecommendationColumn(REC_LEGUMES_ID, "legumes"),
				new RecommendationColumn(REC_WHOLE_GRAINS_ID, "whole grains"));
		assertThat(grid.ingredients()).hasSize(2);

		StagedAlternativeIngredient lentils = grid.ingredients().get(0);
		assertThat(lentils.id()).isEqualTo(AI_LENTILS_ID);
		assertThat(lentils.name()).isEqualTo("Lentils");
		assertThat(lentils.published()).isTrue();
		assertThat(lentils.version()).isZero();
		assertThat(lentils.linkedRecommendationIds()).containsExactlyInAnyOrder(REC_LEGUMES_ID);
		assertThat(lentils.stagedRecommendationIds()).containsExactlyInAnyOrder(REC_WHOLE_GRAINS_ID);
		assertThat(lentils.translations())
				.containsEntry(RecipeLanguage.EL, TranslationState.PRESENT)
				.containsEntry(RecipeLanguage.NL, TranslationState.STAGED)
				.containsEntry(RecipeLanguage.LT, TranslationState.MISSING);

		StagedAlternativeIngredient tofu = grid.ingredients().get(1);
		assertThat(tofu.id()).isEqualTo(AI_TOFU_ID);
		assertThat(tofu.published()).isFalse();
		assertThat(tofu.version()).isEqualTo(2L);
		assertThat(tofu.linkedRecommendationIds()).isEmpty();
		assertThat(tofu.stagedRecommendationIds()).containsExactlyInAnyOrder(REC_LEGUMES_ID);
		assertThat(tofu.translations().values()).containsOnly(TranslationState.MISSING);

		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void recommendationGridRejectsANonAdminWithoutReadingData() {
		assertThatThrownBy(() -> newService().recommendationGrid(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).listForBackoffice(any());
		verify(alternativeIngredientDao, never()).listForBackoffice(any());
		verify(alternativeIngredientDao, never()).findTranslationLangs(any());
		verify(alternativeIngredientDao, never()).findMasterRecommendationLinks(any());
		verify(alternativeIngredientDao, never()).findStagedRecommendationLinks(any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void toggleRecommendationStagesInATransactionForAnAdmin() {
		when(alternativeIngredientDao.toggleRecommendationLink(any(), eq(AI_LENTILS_ID), eq(REC_LEGUMES_ID), eq(true)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().toggleRecommendation(adminUser(), AI_LENTILS_ID, REC_LEGUMES_ID, true).await().atMost(AWAIT);

		verify(alternativeIngredientDao).toggleRecommendationLink(any(), eq(AI_LENTILS_ID), eq(REC_LEGUMES_ID), eq(true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void toggleRecommendationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().toggleRecommendation(nonAdminUser(), AI_LENTILS_ID, REC_LEGUMES_ID, true).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).toggleRecommendationLink(any(), any(), any(), anyBoolean());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void discardRemovesAWorkingCopyOnlyUnreferencedIngredientForAnAdmin() {
		when(alternativeIngredientDao.findEditableById(any(), eq(AI_TOFU_ID)))
				.thenReturn(Uni.createFrom().item(new ReferenceDetails("Tofu", null, 2L, false)));
		when(suggestionTemplateDao.countTemplatesByAlternative(any(), eq(AI_TOFU_ID)))
				.thenReturn(Uni.createFrom().item(0L));
		when(alternativeIngredientDao.discardAlternativeIngredient(any(), eq(AI_TOFU_ID)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().discardAlternativeIngredient(adminUser(), AI_TOFU_ID).await().atMost(AWAIT);

		verify(alternativeIngredientDao).discardAlternativeIngredient(any(), eq(AI_TOFU_ID));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void discardRefusesAPublishedIngredientWithoutCheckingReferencesOrRemoving() {
		when(alternativeIngredientDao.findEditableById(any(), eq(AI_LENTILS_ID)))
				.thenReturn(Uni.createFrom().item(new ReferenceDetails("Lentils", null, 3L, true)));

		assertThatThrownBy(() -> newService().discardAlternativeIngredient(adminUser(), AI_LENTILS_ID).await().atMost(AWAIT))
				.isInstanceOf(EntityInUseException.class);
		verify(suggestionTemplateDao, never()).countTemplatesByAlternative(any(), any());
		verify(alternativeIngredientDao, never()).discardAlternativeIngredient(any(), any());
	}

	@Test
	void discardRefusesAReferencedIngredientWithoutRemoving() {
		when(alternativeIngredientDao.findEditableById(any(), eq(AI_TOFU_ID)))
				.thenReturn(Uni.createFrom().item(new ReferenceDetails("Tofu", null, 2L, false)));
		when(suggestionTemplateDao.countTemplatesByAlternative(any(), eq(AI_TOFU_ID)))
				.thenReturn(Uni.createFrom().item(2L));

		assertThatThrownBy(() -> newService().discardAlternativeIngredient(adminUser(), AI_TOFU_ID).await().atMost(AWAIT))
				.isInstanceOf(EntityInUseException.class);
		verify(alternativeIngredientDao, never()).discardAlternativeIngredient(any(), any());
	}

	@Test
	void discardRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().discardAlternativeIngredient(nonAdminUser(), AI_TOFU_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).findEditableById(any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private BackofficeAlternativeIngredientsServiceImpl newService() {
		return new BackofficeAlternativeIngredientsServiceImpl(
				alternativeIngredientDao, recommendationDao, suggestionTemplateDao, persistenceContextFactory, new AuthorizationImpl());
	}

	private static User adminUser() {
		return user(EnumSet.of(Role.ADMIN));
	}

	private static User nonAdminUser() {
		return user(EnumSet.of(Role.CITIZEN));
	}

	private static User user(EnumSet<Role> roles) {
		return ImmutableUser.builder()
				.id(new UserIdImpl("00000000-0000-0000-0000-000000000009"))
				.name("editor@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(roles)
				.build();
	}
}
