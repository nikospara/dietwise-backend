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
import eu.dietwise.services.model.recommendations.MasterOverride;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.v1.types.StagedRecommendation;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.TypeOfRecommendation;
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
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", TypeOfRecommendation.LIMITED, "Cured and smoked.", "Cured and smoked meats."))));
		when(recommendationDao.findMasterOverrides(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new TranslationLangs(EnumSet.of(RecipeLanguage.EL), EnumSet.noneOf(RecipeLanguage.class), EnumSet.noneOf(RecipeLanguage.class)))));

		List<StagedRecommendation> result = newService().listRecommendations(adminUser()).await().atMost(AWAIT);

		assertThat(result).hasSize(1);
		StagedRecommendation r = result.get(0);
		assertThat(r.id()).isEqualTo(RECOMMENDATION_ID);
		assertThat(r.name()).isEqualTo("Decrease processed meat");
		assertThat(r.componentForScoring()).isEqualTo("processed meat");
		assertThat(r.typeOfRecommendation()).isEqualTo(TypeOfRecommendation.LIMITED);
		assertThat(r.explanationForLlm()).isEqualTo("Cured and smoked.");
		assertThat(r.explanationChanged()).isFalse();
		assertThat(r.humanFriendlyDisplay()).isEqualTo("Cured and smoked meats.");
		assertThat(r.humanFriendlyDisplayChanged()).isFalse();
		assertThat(r.version()).isEqualTo(0L);
		assertThat(r.translations().get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(r.translations().get(RecipeLanguage.NL)).isEqualTo(TranslationState.MISSING);
		assertThat(r.translations().get(RecipeLanguage.LT)).isEqualTo(TranslationState.MISSING);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRecommendationsOverlaysStagedMasterTextWithItsVersionAndPerFieldChangeFlags() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", TypeOfRecommendation.LIMITED, "Master explanation.", "Master display."))));
		when(recommendationDao.findMasterOverrides(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new MasterOverride("Staged explanation.", "Master display.", 3L))));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of()));

		StagedRecommendation r = newService().listRecommendations(adminUser()).await().atMost(AWAIT).get(0);

		assertThat(r.explanationForLlm()).isEqualTo("Staged explanation.");
		assertThat(r.explanationChanged()).isTrue();
		assertThat(r.humanFriendlyDisplay()).isEqualTo("Master display.");
		assertThat(r.humanFriendlyDisplayChanged()).isFalse();
		assertThat(r.version()).isEqualTo(3L);
	}

	@Test
	void listRecommendationsMarksHumanFriendlyDisplayChangedWhenOnlyItDiffersFromMaster() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", TypeOfRecommendation.LIMITED, "Master explanation.", "Master display."))));
		when(recommendationDao.findMasterOverrides(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new MasterOverride("Master explanation.", "Staged display.", 5L))));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of()));

		StagedRecommendation r = newService().listRecommendations(adminUser()).await().atMost(AWAIT).get(0);

		assertThat(r.explanationForLlm()).isEqualTo("Master explanation.");
		assertThat(r.explanationChanged()).isFalse();
		assertThat(r.humanFriendlyDisplay()).isEqualTo("Staged display.");
		assertThat(r.humanFriendlyDisplayChanged()).isTrue();
		assertThat(r.version()).isEqualTo(5L);
	}

	@Test
	void listRecommendationsMarksEveryLanguageMissingWhenNoTranslationsAreReported() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Increase legumes", "legumes", TypeOfRecommendation.ENCOURAGED, null, null))));
		when(recommendationDao.findMasterOverrides(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of()));

		List<StagedRecommendation> result = newService().listRecommendations(adminUser()).await().atMost(AWAIT);

		StagedRecommendation r = result.get(0);
		assertThat(r.explanationForLlm()).isNull();
		assertThat(r.explanationChanged()).isFalse();
		assertThat(r.humanFriendlyDisplay()).isNull();
		assertThat(r.humanFriendlyDisplayChanged()).isFalse();
		assertThat(r.translations()).containsOnlyKeys(RecipeLanguage.EL, RecipeLanguage.LT, RecipeLanguage.NL);
		assertThat(Set.copyOf(r.translations().values())).containsExactly(TranslationState.MISSING);
	}

	@Test
	void listRecommendationsRejectsANonAdminWithoutReadingData() {
		assertThatThrownBy(() -> newService().listRecommendations(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).listForBackoffice(any());
		verify(recommendationDao, never()).findMasterOverrides(any());
		verify(recommendationDao, never()).findTranslationLangs(any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageMasterStagesInATransactionForAnAdminAndReturnsTheNewVersion() {
		when(recommendationDao.stageMaster(any(), eq(RECOMMENDATION_ID), eq("New explanation."), eq("New display."), eq(2L)))
				.thenReturn(Uni.createFrom().item(3L));

		long version = newService().stageMaster(adminUser(), RECOMMENDATION_ID, "New explanation.", "New display.", 2L).await().atMost(AWAIT);

		assertThat(version).isEqualTo(3L);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertMasterRevertsInATransactionForAnAdmin() {
		when(recommendationDao.revertMaster(any(), eq(RECOMMENDATION_ID), eq(4L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertMaster(adminUser(), RECOMMENDATION_ID, 4L).await().atMost(AWAIT);

		verify(recommendationDao).revertMaster(any(), eq(RECOMMENDATION_ID), eq(4L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageMasterRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageMaster(nonAdminUser(), RECOMMENDATION_ID, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).stageMaster(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertMasterRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertMaster(nonAdminUser(), RECOMMENDATION_ID, 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).revertMaster(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listRecommendationsReflectsStagedTranslationLanguages() {
		when(recommendationDao.listForBackoffice(any())).thenReturn(Uni.createFrom().item(List.of(
				new BackofficeRecommendation(RECOMMENDATION_ID, "Decrease processed meat", "processed meat", TypeOfRecommendation.LIMITED, "Cured and smoked.", "Cured and smoked meats."))));
		when(recommendationDao.findMasterOverrides(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(recommendationDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				RECOMMENDATION_ID, new TranslationLangs(EnumSet.of(RecipeLanguage.EL, RecipeLanguage.NL), EnumSet.noneOf(RecipeLanguage.class), EnumSet.of(RecipeLanguage.NL)))));

		StagedRecommendation r = newService().listRecommendations(adminUser()).await().atMost(AWAIT).get(0);

		assertThat(r.translations().get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(r.translations().get(RecipeLanguage.NL)).isEqualTo(TranslationState.STAGED);
		assertThat(r.translations().get(RecipeLanguage.LT)).isEqualTo(TranslationState.MISSING);
	}

	@Test
	void translationsForEditReturnsPerLanguageDetailsForAnAdminWithoutOpeningATransaction() {
		Map<RecipeLanguage, RecommendationTranslationDetails> details = Map.of(
				RecipeLanguage.EL, new RecommendationTranslationDetails("Όνομα", "συστατικό", "Εξήγηση.", "Εμφάνιση.", 2L),
				RecipeLanguage.LT, new RecommendationTranslationDetails(null, null, null, null, 0L),
				RecipeLanguage.NL, new RecommendationTranslationDetails(null, null, null, null, 0L));
		when(recommendationDao.findTranslationsForEdit(any(), eq(RECOMMENDATION_ID)))
				.thenReturn(Uni.createFrom().item(details));

		Map<RecipeLanguage, RecommendationTranslationDetails> result =
				newService().translationsForEdit(adminUser(), RECOMMENDATION_ID).await().atMost(AWAIT);

		assertThat(result).isEqualTo(details);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageTranslationStagesInATransactionForAnAdmin() {
		when(recommendationDao.stageTranslation(any(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.EL), eq("Όνομα"), eq("συστατικό"), eq("Εξήγηση."), eq("Εμφάνιση."), eq(2L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageTranslation(adminUser(), RECOMMENDATION_ID, RecipeLanguage.EL, "Όνομα", "συστατικό", "Εξήγηση.", "Εμφάνιση.", 2L).await().atMost(AWAIT);

		verify(recommendationDao).stageTranslation(any(), eq(RECOMMENDATION_ID), eq(RecipeLanguage.EL), eq("Όνομα"), eq("συστατικό"), eq("Εξήγηση."), eq("Εμφάνιση."), eq(2L));
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
		assertThatThrownBy(() -> newService().stageTranslation(adminUser(), RECOMMENDATION_ID, RecipeLanguage.EN, "x", "y", "z", "w", 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(recommendationDao, never()).stageTranslation(any(), any(), any(), any(), any(), any(), any(), anyLong());
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
		assertThatThrownBy(() -> newService().stageTranslation(nonAdminUser(), RECOMMENDATION_ID, RecipeLanguage.EL, "x", "y", "z", "w", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(recommendationDao, never()).stageTranslation(any(), any(), any(), any(), any(), any(), any(), anyLong());
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
