package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.recommendations.ExplanationOverride;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.types.StagedRecommendation;
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
class BackofficeRecommendationsServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);
	private static final UUID RECOMMENDATION_ID = UUID.fromString("a1a2a3a4-0009-4f5a-8b9c-0d1e2f3a0009");

	@Mock
	private RecommendationDao recommendationDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void listRecommendationsMapsMasterRowsAndPerLanguageStateForAnAdmin() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", RecommendationWeight.LIMITED, "Cured and smoked."))));
		when(recommendationDao.findExplanationOverrides(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new TranslationLangs(EnumSet.of(RecipeLanguage.EL), EnumSet.noneOf(RecipeLanguage.class)))));

		List<StagedRecommendation> result = newService().listRecommendations(adminUser()).await().atMost(AWAIT);

		assertThat(result).hasSize(1);
		StagedRecommendation r = result.get(0);
		assertThat(r.id()).isEqualTo(RECOMMENDATION_ID);
		assertThat(r.name()).isEqualTo("Decrease processed meat");
		assertThat(r.componentForScoring()).isEqualTo("processed meat");
		assertThat(r.weight()).isEqualTo(RecommendationWeight.LIMITED);
		assertThat(r.explanationForLlm()).isEqualTo("Cured and smoked.");
		assertThat(r.explanationChanged()).isFalse();
		assertThat(r.version()).isEqualTo(0L);
		assertThat(r.translations().get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(r.translations().get(RecipeLanguage.NL)).isEqualTo(TranslationState.MISSING);
		assertThat(r.translations().get(RecipeLanguage.LT)).isEqualTo(TranslationState.MISSING);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRecommendationsOverlaysAStagedExplanationWithItsVersionAndChangeFlag() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", RecommendationWeight.LIMITED, "Master explanation."))));
		when(recommendationDao.findExplanationOverrides(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new ExplanationOverride("Staged explanation.", 3L))));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of()));

		StagedRecommendation r = newService().listRecommendations(adminUser()).await().atMost(AWAIT).get(0);

		assertThat(r.explanationForLlm()).isEqualTo("Staged explanation.");
		assertThat(r.explanationChanged()).isTrue();
		assertThat(r.version()).isEqualTo(3L);
	}

	@Test
	void listRecommendationsMarksEveryLanguageMissingWhenNoTranslationsAreReported() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Increase legumes", "legumes", RecommendationWeight.ENCOURAGED, null))));
		when(recommendationDao.findExplanationOverrides(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of()));

		List<StagedRecommendation> result = newService().listRecommendations(adminUser()).await().atMost(AWAIT);

		StagedRecommendation r = result.get(0);
		assertThat(r.explanationForLlm()).isNull();
		assertThat(r.explanationChanged()).isFalse();
		assertThat(r.translations()).containsOnlyKeys(RecipeLanguage.EL, RecipeLanguage.LT, RecipeLanguage.NL);
		assertThat(Set.copyOf(r.translations().values())).containsExactly(TranslationState.MISSING);
	}

	@Test
	void listRecommendationsRejectsANonAdminWithoutReadingData() {
		assertThatThrownBy(() -> newService().listRecommendations(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).listForBackoffice(any());
		verify(recommendationDao, never()).findExplanationOverrides(any());
		verify(recommendationDao, never()).findTranslationLangs(any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageExplanationStagesInATransactionForAnAdminAndReturnsTheNewVersion() {
		when(recommendationDao.stageExplanation(any(), eq(RECOMMENDATION_ID), eq("New explanation."), eq(2L)))
				.thenReturn(Uni.createFrom().item(3L));

		long version = newService().stageExplanation(adminUser(), RECOMMENDATION_ID, "New explanation.", 2L).await().atMost(AWAIT);

		assertThat(version).isEqualTo(3L);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertExplanationRevertsInATransactionForAnAdmin() {
		when(recommendationDao.revertExplanation(any(), eq(RECOMMENDATION_ID), eq(4L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertExplanation(adminUser(), RECOMMENDATION_ID, 4L).await().atMost(AWAIT);

		verify(recommendationDao).revertExplanation(any(), eq(RECOMMENDATION_ID), eq(4L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageExplanationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageExplanation(nonAdminUser(), RECOMMENDATION_ID, "x", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).stageExplanation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertExplanationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertExplanation(nonAdminUser(), RECOMMENDATION_ID, 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).revertExplanation(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRecommendationsReflectsStagedTranslationLanguages() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", RecommendationWeight.LIMITED, "Cured and smoked."))));
		when(recommendationDao.findExplanationOverrides(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new TranslationLangs(EnumSet.of(RecipeLanguage.EL), EnumSet.of(RecipeLanguage.NL)))));

		StagedRecommendation r = newService().listRecommendations(adminUser()).await().atMost(AWAIT).get(0);

		assertThat(r.translations().get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(r.translations().get(RecipeLanguage.NL)).isEqualTo(TranslationState.STAGED);
		assertThat(r.translations().get(RecipeLanguage.LT)).isEqualTo(TranslationState.MISSING);
	}

	@Test
	void translationsForEditReturnsPerLanguageDetailsForAnAdminWithoutOpeningATransaction() {
		Map<RecipeLanguage, RecommendationTranslationDetails> details = Map.of(
				RecipeLanguage.EL, new RecommendationTranslationDetails("Όνομα", "συστατικό", "Εξήγηση.", 2L),
				RecipeLanguage.LT, new RecommendationTranslationDetails(null, null, null, 0L),
				RecipeLanguage.NL, new RecommendationTranslationDetails(null, null, null, 0L));
		when(recommendationDao.findTranslationsForEdit(any(), eq(RECOMMENDATION_ID)))
				.thenReturn(Uni.createFrom().item(details));

		Map<RecipeLanguage, RecommendationTranslationDetails> result =
				newService().translationsForEdit(adminUser(), RECOMMENDATION_ID).await().atMost(AWAIT);

		assertThat(result).isEqualTo(details);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageTranslationStagesInATransactionForAnAdmin() {
		when(recommendationDao.stageTranslation(any(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.EL), eq("Όνομα"), eq("συστατικό"), eq("Εξήγηση."), eq(2L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageTranslation(adminUser(), RECOMMENDATION_ID, RecipeLanguage.EL, "Όνομα", "συστατικό", "Εξήγηση.", 2L).await().atMost(AWAIT);

		verify(recommendationDao).stageTranslation(any(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.EL), eq("Όνομα"), eq("συστατικό"), eq("Εξήγηση."), eq(2L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertTranslationRevertsInATransactionForAnAdmin() {
		when(recommendationDao.revertTranslation(any(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.NL), eq(3L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertTranslation(adminUser(), RECOMMENDATION_ID, RecipeLanguage.NL, 3L).await().atMost(AWAIT);

		verify(recommendationDao).revertTranslation(any(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.NL), eq(3L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageTranslationRejectsEnglishAsATranslationTargetWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageTranslation(adminUser(), RECOMMENDATION_ID, RecipeLanguage.EN, "x", "y", "z", 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(recommendationDao, never()).stageTranslation(any(), any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTranslationRejectsEnglishAsATranslationTargetWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTranslation(adminUser(), RECOMMENDATION_ID, RecipeLanguage.EN, 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(recommendationDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void translationsForEditRejectsANonAdminWithoutReadingData() {
		assertThatThrownBy(() -> newService().translationsForEdit(nonAdminUser(), RECOMMENDATION_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).findTranslationsForEdit(any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageTranslation(nonAdminUser(), RECOMMENDATION_ID, RecipeLanguage.EL, "x", "y", "z", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).stageTranslation(any(), any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTranslation(nonAdminUser(), RECOMMENDATION_ID, RecipeLanguage.EL, 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private BackofficeRecommendationsServiceImpl newService() {
		return new BackofficeRecommendationsServiceImpl(recommendationDao, persistenceContextFactory, new AuthorizationImpl());
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
