package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.model.suggestions.FieldTranslationLangs;
import eu.dietwise.services.model.suggestions.NewSuggestionTemplate;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.services.v1.types.AddedTemplate;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackofficeSuggestionTemplatesServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);
	private static final UUID RULE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private static final UUID OTHER_TEMPLATE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
	private static final UUID NEW_TEMPLATE_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

	@Mock
	private SuggestionTemplateDao suggestionTemplateDao;

	@Mock
	private AlternativeIngredientDao alternativeIngredientDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@BeforeEach
	void noStagedTemplatesByDefault() {
		lenient().when(suggestionTemplateDao.findFieldTranslationLangsByRule(any(), any())).thenReturn(Uni.createFrom().item(Map.of()));
		lenient().when(suggestionTemplateDao.findActiveByRule(any(), any())).thenReturn(Uni.createFrom().item(Map.of()));
		lenient().when(suggestionTemplateDao.findNewByRule(any(), any())).thenReturn(Uni.createFrom().item(List.of()));
		lenient().when(suggestionTemplateDao.findAlternativeIdsByRule(any(), any())).thenReturn(Uni.createFrom().item(Map.of()));
		lenient().when(alternativeIngredientDao.findStagedNames(any())).thenReturn(Uni.createFrom().item(Map.of()));
		lenient().when(alternativeIngredientDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of()));
	}

	@Test
	void listSuggestionTemplatesOverlaysStagedTemplatesOnMasterForAnAdmin() {
		when(suggestionTemplateDao.findByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(List.of(
				suggestionTemplate(TEMPLATE_ID, "Brown lentils (cooked)", "Not for burgers without binder", "1:1", "Dry sauté"),
				suggestionTemplate(OTHER_TEMPLATE_ID, "Soy mince", null, null, null))));
		when(suggestionTemplateDao.findStagedOverlayByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of(
				TEMPLATE_ID, new StagedSuggestionTemplateOverlay("No binder needed", "1:1", "Dry sauté", true, 2L))));

		List<StagedSuggestionTemplate> templates = newService()
				.listSuggestionTemplates(adminUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT);

		assertThat(templates).hasSize(2);
		StagedSuggestionTemplate edited = templates.getFirst();
		assertThat(edited.template().getRestriction()).contains("No binder needed");
		assertThat(edited.changedFields()).containsExactly(SuggestionTemplateField.RESTRICTION);
		assertThat(edited.active()).isTrue();
		assertThat(edited.activeChanged()).isFalse();
		assertThat(edited.version()).isEqualTo(2L);
		StagedSuggestionTemplate unchanged = templates.get(1);
		assertThat(unchanged.template()).isEqualTo(suggestionTemplate(OTHER_TEMPLATE_ID, "Soy mince", null, null, null));
		assertThat(unchanged.changedFields()).isEmpty();
		assertThat(unchanged.version()).isZero();
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listSuggestionTemplatesRejectsANonAdminWithoutQueryingTheDao() {
		assertThatThrownBy(() -> newService().listSuggestionTemplates(nonAdminUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).findByRule(any(), any());
	}

	@Test
	void listSuggestionTemplatesRejectsAnUnauthenticatedUser() {
		assertThatThrownBy(() -> newService().listSuggestionTemplates(unauthenticatedUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT))
				.isInstanceOf(NotAuthenticatedException.class);
		verify(suggestionTemplateDao, never()).findByRule(any(), any());
	}

	@Test
	void stageSuggestionTemplateFieldStagesTheEditForAnAdminAndReturnsTheNewVersion() {
		when(suggestionTemplateDao.stageField(any(), eq(TEMPLATE_ID), eq(SuggestionTemplateField.RESTRICTION), eq("No binder needed"), eq(0L)))
				.thenReturn(Uni.createFrom().item(1L));

		Long version = newService().stageSuggestionTemplateField(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, "No binder needed", 0L)
				.await().atMost(AWAIT);

		assertThat(version).isEqualTo(1L);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageSuggestionTemplateFieldRejectsAStaleBaseVersion() {
		when(suggestionTemplateDao.stageField(any(), eq(TEMPLATE_ID), eq(SuggestionTemplateField.EQUIVALENCE), eq("1:2"), eq(0L)))
				.thenReturn(Uni.createFrom().failure(new StaleVersionException(null, TEMPLATE_ID)));

		assertThatThrownBy(() -> newService().stageSuggestionTemplateField(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.EQUIVALENCE, "1:2", 0L)
				.await().atMost(AWAIT))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	void stageSuggestionTemplateFieldRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageSuggestionTemplateField(nonAdminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, "x", 0L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).stageField(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertSuggestionTemplateFieldRemovesTheStagedChangeForAnAdmin() {
		when(suggestionTemplateDao.revertField(any(), eq(TEMPLATE_ID), eq(SuggestionTemplateField.RESTRICTION), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertSuggestionTemplateField(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, 1L)
				.await().atMost(AWAIT);

		verify(suggestionTemplateDao).revertField(any(), eq(TEMPLATE_ID), eq(SuggestionTemplateField.RESTRICTION), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertSuggestionTemplateFieldRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertSuggestionTemplateField(nonAdminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).revertField(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listSuggestionTemplatesReportsEffectiveActiveAndWhetherDeactivationIsStaged() {
		when(suggestionTemplateDao.findByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(List.of(
				suggestionTemplate(TEMPLATE_ID, "Brown lentils (cooked)", "Not for burgers without binder", "1:1", "Dry sauté"),
				suggestionTemplate(OTHER_TEMPLATE_ID, "Soy mince", null, null, null))));
		when(suggestionTemplateDao.findStagedOverlayByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of(
				TEMPLATE_ID, new StagedSuggestionTemplateOverlay("Not for burgers without binder", "1:1", "Dry sauté", false, 3L))));
		when(suggestionTemplateDao.findActiveByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of(
				TEMPLATE_ID, true, OTHER_TEMPLATE_ID, false)));

		List<StagedSuggestionTemplate> templates = newService()
				.listSuggestionTemplates(adminUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT);

		StagedSuggestionTemplate stagedDeactivation = templates.getFirst();
		assertThat(stagedDeactivation.active()).isFalse();
		assertThat(stagedDeactivation.activeChanged()).isTrue();
		assertThat(stagedDeactivation.changedFields()).isEmpty();
		assertThat(stagedDeactivation.version()).isEqualTo(3L);
		StagedSuggestionTemplate publishedDeactivated = templates.get(1);
		assertThat(publishedDeactivated.active()).isFalse();
		assertThat(publishedDeactivated.activeChanged()).isFalse();
		assertThat(publishedDeactivated.version()).isZero();
	}

	@Test
	void setSuggestionTemplateActiveStagesTheToggleForAnAdmin() {
		when(suggestionTemplateDao.setActive(any(), eq(TEMPLATE_ID), eq(false), eq(1L))).thenReturn(Uni.createFrom().voidItem());

		newService().setSuggestionTemplateActive(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), false, 1L)
				.await().atMost(AWAIT);

		verify(suggestionTemplateDao).setActive(any(), eq(TEMPLATE_ID), eq(false), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void setSuggestionTemplateActiveRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().setSuggestionTemplateActive(nonAdminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), false, 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).setActive(any(), any(), anyBoolean(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listSuggestionTemplatesReportsThePerFieldTranslationStates() {
		when(suggestionTemplateDao.findByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(List.of(
				suggestionTemplate(TEMPLATE_ID, "Brown lentils (cooked)", "Not for burgers without binder", "1:1", null))));
		when(suggestionTemplateDao.findStagedOverlayByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionTemplateDao.findFieldTranslationLangsByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of(
				TEMPLATE_ID, new FieldTranslationLangs(
						new TranslationLangs(Set.of(RecipeLanguage.EL, RecipeLanguage.NL), Set.of(RecipeLanguage.LT)),
						new TranslationLangs(Set.of(RecipeLanguage.EL), Set.of()),
						new TranslationLangs(Set.of(), Set.of())))));

		StagedSuggestionTemplate template = newService()
				.listSuggestionTemplates(adminUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT).getFirst();

		Map<RecipeLanguage, TranslationState> restriction = template.translations().get(SuggestionTemplateField.RESTRICTION);
		assertThat(restriction.get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(restriction.get(RecipeLanguage.LT)).isEqualTo(TranslationState.STAGED);
		assertThat(restriction.get(RecipeLanguage.NL)).isEqualTo(TranslationState.PRESENT);
		Map<RecipeLanguage, TranslationState> equivalence = template.translations().get(SuggestionTemplateField.EQUIVALENCE);
		assertThat(equivalence.get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(equivalence.get(RecipeLanguage.LT)).isEqualTo(TranslationState.MISSING);
		assertThat(template.translations().get(SuggestionTemplateField.TECHNIQUE_NOTES).values())
				.containsOnly(TranslationState.MISSING);
	}

	@Test
	void templateFieldTranslationsForEditReturnsTheEffectiveTranslationsForAnAdmin() {
		when(suggestionTemplateDao.findFieldTranslationsForEdit(any(), eq(TEMPLATE_ID), eq(SuggestionTemplateField.RESTRICTION)))
				.thenReturn(Uni.createFrom().item(Map.of(RecipeLanguage.EL, new VersionedText("Greek restriction", 3L))));

		Map<RecipeLanguage, VersionedText> translations = newService()
				.templateFieldTranslationsForEdit(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION)
				.await().atMost(AWAIT);

		assertThat(translations.get(RecipeLanguage.EL)).isEqualTo(new VersionedText("Greek restriction", 3L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void templateFieldTranslationsForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().templateFieldTranslationsForEdit(nonAdminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).findFieldTranslationsForEdit(any(), any(), any());
	}

	@Test
	void stageTemplateFieldTranslationStagesTheTranslationForAnAdmin() {
		when(suggestionTemplateDao.stageFieldTranslation(any(), eq(TEMPLATE_ID), eq(RecipeLanguage.EL), eq(SuggestionTemplateField.RESTRICTION), eq("Greek restriction"), eq(0L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageTemplateFieldTranslation(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, RecipeLanguage.EL, "Greek restriction", 0L)
				.await().atMost(AWAIT);

		verify(suggestionTemplateDao).stageFieldTranslation(any(), eq(TEMPLATE_ID), eq(RecipeLanguage.EL), eq(SuggestionTemplateField.RESTRICTION), eq("Greek restriction"), eq(0L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageTemplateFieldTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageTemplateFieldTranslation(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, RecipeLanguage.EN, "x", 0L)
				.await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(suggestionTemplateDao, never()).stageFieldTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageTemplateFieldTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageTemplateFieldTranslation(nonAdminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, RecipeLanguage.EL, "x", 0L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).stageFieldTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTemplateFieldTranslationRemovesTheStagedTranslationForAnAdmin() {
		when(suggestionTemplateDao.revertFieldTranslation(any(), eq(TEMPLATE_ID), eq(RecipeLanguage.EL), eq(SuggestionTemplateField.RESTRICTION), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertTemplateFieldTranslation(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, RecipeLanguage.EL, 1L)
				.await().atMost(AWAIT);

		verify(suggestionTemplateDao).revertFieldTranslation(any(), eq(TEMPLATE_ID), eq(RecipeLanguage.EL), eq(SuggestionTemplateField.RESTRICTION), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertTemplateFieldTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTemplateFieldTranslation(adminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, RecipeLanguage.EN, 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(suggestionTemplateDao, never()).revertFieldTranslation(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTemplateFieldTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTemplateFieldTranslation(nonAdminUser(), new GenericSuggestionTemplateId(TEMPLATE_ID.toString()), SuggestionTemplateField.RESTRICTION, RecipeLanguage.EL, 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).revertFieldTranslation(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void listSuggestionTemplatesAppendsWorkingCopyOnlyTemplatesMarkedUnpublished() {
		when(suggestionTemplateDao.findByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(List.of(
				suggestionTemplate(TEMPLATE_ID, "Brown lentils (cooked)", "Not for burgers without binder", "1:1", "Dry sauté"))));
		when(suggestionTemplateDao.findStagedOverlayByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionTemplateDao.findNewByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(List.of(
				new NewSuggestionTemplate(suggestionTemplate(NEW_TEMPLATE_ID, "Smoked tofu cubes", null, null, null), 1L))));

		List<StagedSuggestionTemplate> templates = newService()
				.listSuggestionTemplates(adminUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT);

		assertThat(templates).hasSize(2);
		StagedSuggestionTemplate published = templates.getFirst();
		assertThat(published.template().getId().asString()).isEqualTo(TEMPLATE_ID.toString());
		assertThat(published.published()).isTrue();
		StagedSuggestionTemplate added = templates.get(1);
		assertThat(added.template().getId().asString()).isEqualTo(NEW_TEMPLATE_ID.toString());
		assertThat(added.template().getAlternative().asString()).isEqualTo("Smoked tofu cubes");
		assertThat(added.published()).isFalse();
		assertThat(added.active()).isTrue();
		assertThat(added.activeChanged()).isFalse();
		assertThat(added.changedFields()).isEmpty();
		assertThat(added.version()).isEqualTo(1L);
	}

	@Test
	void addSuggestionTemplateCreatesANewTemplateWhenTheRuleHasNoneForTheAlternative() {
		when(suggestionTemplateDao.findTemplateIdByRuleAndAlternative(any(), eq(RULE_ID), eq(ALTERNATIVE_INGREDIENT_ID)))
				.thenReturn(Uni.createFrom().item(Optional.empty()));
		when(suggestionTemplateDao.addTemplate(any(), eq(RULE_ID), eq(ALTERNATIVE_INGREDIENT_ID)))
				.thenReturn(Uni.createFrom().item(NEW_TEMPLATE_ID));

		AddedTemplate added = newService().addSuggestionTemplate(adminUser(), new GenericRuleId(RULE_ID.toString()), ALTERNATIVE_INGREDIENT_ID)
				.await().atMost(AWAIT);

		assertThat(added).isEqualTo(new AddedTemplate(NEW_TEMPLATE_ID, true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void addSuggestionTemplateReturnsTheExistingTemplateWithoutCreatingWhenTheAlternativeIsAlreadyUsed() {
		when(suggestionTemplateDao.findTemplateIdByRuleAndAlternative(any(), eq(RULE_ID), eq(ALTERNATIVE_INGREDIENT_ID)))
				.thenReturn(Uni.createFrom().item(Optional.of(TEMPLATE_ID)));

		AddedTemplate added = newService().addSuggestionTemplate(adminUser(), new GenericRuleId(RULE_ID.toString()), ALTERNATIVE_INGREDIENT_ID)
				.await().atMost(AWAIT);

		assertThat(added).isEqualTo(new AddedTemplate(TEMPLATE_ID, false));
		verify(suggestionTemplateDao, never()).addTemplate(any(), any(), any());
	}

	@Test
	void addSuggestionTemplateRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().addSuggestionTemplate(nonAdminUser(), new GenericRuleId(RULE_ID.toString()), ALTERNATIVE_INGREDIENT_ID)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).findTemplateIdByRuleAndAlternative(any(), any(), any());
		verify(suggestionTemplateDao, never()).addTemplate(any(), any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void discardSuggestionTemplateDelegatesToTheDaoForAnAdmin() {
		when(suggestionTemplateDao.discardTemplate(any(), eq(NEW_TEMPLATE_ID), eq(1L))).thenReturn(Uni.createFrom().voidItem());

		newService().discardSuggestionTemplate(adminUser(), new GenericSuggestionTemplateId(NEW_TEMPLATE_ID.toString()), 1L)
				.await().atMost(AWAIT);

		verify(suggestionTemplateDao).discardTemplate(any(), eq(NEW_TEMPLATE_ID), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void discardSuggestionTemplateRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().discardSuggestionTemplate(nonAdminUser(), new GenericSuggestionTemplateId(NEW_TEMPLATE_ID.toString()), 1L)
				.await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(suggestionTemplateDao, never()).discardTemplate(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void alternativeIngredientOptionsReturnsOptionsForAnAdmin() {
		when(alternativeIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Smoked tofu cubes"))));

		List<ReferenceOption> options = newService().alternativeIngredientOptions(adminUser()).await().atMost(AWAIT);

		assertThat(options).containsExactly(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Smoked tofu cubes"));
	}

	@Test
	void alternativeIngredientOptionsRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().alternativeIngredientOptions(nonAdminUser()).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).listOptions(any());
	}

	@Test
	void listSuggestionTemplatesOverlaysAStagedAlternativeNameIdAndTranslations() {
		when(suggestionTemplateDao.findByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(List.of(
				suggestionTemplate(TEMPLATE_ID, "Smoked tofu cubes", "Not for burgers without binder", "1:1", "Dry sauté"))));
		when(suggestionTemplateDao.findStagedOverlayByRule(any(), eq(RULE_ID))).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionTemplateDao.findAlternativeIdsByRule(any(), eq(RULE_ID)))
				.thenReturn(Uni.createFrom().item(Map.of(TEMPLATE_ID, ALTERNATIVE_INGREDIENT_ID)));
		when(alternativeIngredientDao.findStagedNames(any()))
				.thenReturn(Uni.createFrom().item(Map.of(ALTERNATIVE_INGREDIENT_ID, "Smoked tofu cubes (revised)")));
		when(alternativeIngredientDao.findTranslationLangs(any())).thenReturn(Uni.createFrom().item(Map.of(
				ALTERNATIVE_INGREDIENT_ID, new TranslationLangs(Set.of(RecipeLanguage.EL), Set.of(RecipeLanguage.NL)))));

		StagedSuggestionTemplate template = newService()
				.listSuggestionTemplates(adminUser(), new GenericRuleId(RULE_ID.toString())).await().atMost(AWAIT).getFirst();

		assertThat(template.template().getAlternative().asString()).isEqualTo("Smoked tofu cubes (revised)");
		assertThat(template.alternativeIngredientId()).isEqualTo(ALTERNATIVE_INGREDIENT_ID);
		assertThat(template.alternativeIngredientTranslations().get(RecipeLanguage.EL)).isEqualTo(TranslationState.PRESENT);
		assertThat(template.alternativeIngredientTranslations().get(RecipeLanguage.NL)).isEqualTo(TranslationState.STAGED);
		assertThat(template.alternativeIngredientTranslations().get(RecipeLanguage.LT)).isEqualTo(TranslationState.MISSING);
	}

	private static SuggestionTemplate suggestionTemplate(UUID id, String alternativeName, String restriction, String equivalence, String techniqueNotes) {
		return ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(id.toString()))
				.alternative(new AlternativeIngredientImpl(alternativeName))
				.restriction(Optional.ofNullable(restriction))
				.equivalence(Optional.ofNullable(equivalence))
				.techniqueNotes(Optional.ofNullable(techniqueNotes))
				.build();
	}

	private BackofficeSuggestionTemplatesServiceImpl newService() {
		return new BackofficeSuggestionTemplatesServiceImpl(suggestionTemplateDao, alternativeIngredientDao, persistenceContextFactory, new AuthorizationImpl());
	}

	private static User adminUser() {
		return user(EnumSet.of(Role.ADMIN), false);
	}

	private static User nonAdminUser() {
		return user(EnumSet.of(Role.CITIZEN), false);
	}

	private static User unauthenticatedUser() {
		return user(EnumSet.noneOf(Role.class), true);
	}

	private static User user(EnumSet<Role> roles, boolean unauthenticated) {
		return ImmutableUser.builder()
				.id(new UserIdImpl("00000000-0000-0000-0000-000000000009"))
				.name("editor@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(unauthenticated)
				.roles(roles)
				.build();
	}
}
