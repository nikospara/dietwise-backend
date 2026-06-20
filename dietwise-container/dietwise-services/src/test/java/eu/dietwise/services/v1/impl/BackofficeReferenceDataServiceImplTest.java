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
import java.util.UUID;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.StaleVersionException;
import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.v1.types.AlternativeIngredientForEdit;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackofficeReferenceDataServiceImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);
	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID ROLE_OR_TECHNIQUE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
	private static final UUID OTHER_TRIGGER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
	private static final UUID OTHER_ROLE_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
	private static final UUID NEW_TEMPLATE_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

	@Mock
	private TriggerIngredientDao triggerIngredientDao;

	@Mock
	private RoleOrTechniqueDao roleOrTechniqueDao;

	@Mock
	private AlternativeIngredientDao alternativeIngredientDao;

	@Mock
	private SuggestionTemplateDao suggestionTemplateDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Test
	void triggerIngredientForEditReturnsEffectiveDetailsForAnAdmin() {
		when(triggerIngredientDao.findEditableById(any(), eq(TRIGGER_INGREDIENT_ID)))
				.thenReturn(Uni.createFrom().item(new ReferenceDetails("Beef", "Red meat.", 3L, true)));

		ReferenceDetails details = newService().triggerIngredientForEdit(adminUser(), TRIGGER_INGREDIENT_ID).await().atMost(AWAIT);

		assertThat(details).isEqualTo(new ReferenceDetails("Beef", "Red meat.", 3L, true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void triggerIngredientForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().triggerIngredientForEdit(nonAdminUser(), TRIGGER_INGREDIENT_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).findEditableById(any(), any());
	}

	@Test
	void roleOrTechniqueForEditReturnsEffectiveDetailsForAnAdmin() {
		when(roleOrTechniqueDao.findEditableById(any(), eq(ROLE_OR_TECHNIQUE_ID)))
				.thenReturn(Uni.createFrom().item(new ReferenceDetails("minced in sauce", null, 0L, true)));

		ReferenceDetails details = newService().roleOrTechniqueForEdit(adminUser(), ROLE_OR_TECHNIQUE_ID).await().atMost(AWAIT);

		assertThat(details).isEqualTo(new ReferenceDetails("minced in sauce", null, 0L, true));
	}

	@Test
	void roleOrTechniqueForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().roleOrTechniqueForEdit(nonAdminUser(), ROLE_OR_TECHNIQUE_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).findEditableById(any(), any());
	}

	@Test
	void editTriggerIngredientStagesTheEditForAnAdmin() {
		when(triggerIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"))));
		when(triggerIngredientDao.editTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq("Bovine"), eq("Red meat."), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().editTriggerIngredient(adminUser(), TRIGGER_INGREDIENT_ID, "Bovine", "Red meat.", 1L).await().atMost(AWAIT);

		verify(triggerIngredientDao).editTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq("Bovine"), eq("Red meat."), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void editTriggerIngredientExcludesItselfFromTheUniquenessCheckWhenKeepingItsName() {
		when(triggerIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"))));
		when(triggerIngredientDao.editTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq("Beef"), eq("Updated explanation."), eq(2L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().editTriggerIngredient(adminUser(), TRIGGER_INGREDIENT_ID, "Beef", "Updated explanation.", 2L).await().atMost(AWAIT);

		verify(triggerIngredientDao).editTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq("Beef"), eq("Updated explanation."), eq(2L));
	}

	@Test
	void editTriggerIngredientRejectsRenamingToAnotherEntrysNameCaseInsensitively() {
		when(triggerIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(
				new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"),
				new ReferenceOption(OTHER_TRIGGER_ID, "Soy sauce"))));

		assertThatThrownBy(() -> newService().editTriggerIngredient(adminUser(), TRIGGER_INGREDIENT_ID, "soy sauce", "x", 1L).await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(triggerIngredientDao, never()).editTriggerIngredient(any(), any(), any(), any(), anyLong());
	}

	@Test
	void editTriggerIngredientRejectsAStaleBaseVersion() {
		when(triggerIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Beef"))));
		when(triggerIngredientDao.editTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq("Bovine"), eq("x"), eq(9L)))
				.thenReturn(Uni.createFrom().failure(new StaleVersionException(null, TRIGGER_INGREDIENT_ID)));

		assertThatThrownBy(() -> newService().editTriggerIngredient(adminUser(), TRIGGER_INGREDIENT_ID, "Bovine", "x", 9L).await().atMost(AWAIT))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	void editTriggerIngredientRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().editTriggerIngredient(nonAdminUser(), TRIGGER_INGREDIENT_ID, "Bovine", "x", 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).listOptions(any());
		verify(triggerIngredientDao, never()).editTriggerIngredient(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void editRoleOrTechniqueStagesTheEditForAnAdmin() {
		when(roleOrTechniqueDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(ROLE_OR_TECHNIQUE_ID, "minced in sauce"))));
		when(roleOrTechniqueDao.editRoleOrTechnique(any(), eq(ROLE_OR_TECHNIQUE_ID), eq("folded through"), eq("Mixed in."), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().editRoleOrTechnique(adminUser(), ROLE_OR_TECHNIQUE_ID, "folded through", "Mixed in.", 1L).await().atMost(AWAIT);

		verify(roleOrTechniqueDao).editRoleOrTechnique(any(), eq(ROLE_OR_TECHNIQUE_ID), eq("folded through"), eq("Mixed in."), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void editRoleOrTechniqueRejectsRenamingToAnotherEntrysNameCaseInsensitively() {
		when(roleOrTechniqueDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(
				new ReferenceOption(ROLE_OR_TECHNIQUE_ID, "minced in sauce"),
				new ReferenceOption(OTHER_ROLE_ID, "seasoning"))));

		assertThatThrownBy(() -> newService().editRoleOrTechnique(adminUser(), ROLE_OR_TECHNIQUE_ID, "Seasoning", "x", 1L).await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(roleOrTechniqueDao, never()).editRoleOrTechnique(any(), any(), any(), any(), anyLong());
	}

	@Test
	void editRoleOrTechniqueRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().editRoleOrTechnique(nonAdminUser(), ROLE_OR_TECHNIQUE_ID, "folded through", "x", 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).listOptions(any());
		verify(roleOrTechniqueDao, never()).editRoleOrTechnique(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTriggerIngredientRemovesTheStagedEditForAnAdmin() {
		when(triggerIngredientDao.revertTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertTriggerIngredient(adminUser(), TRIGGER_INGREDIENT_ID, 1L).await().atMost(AWAIT);

		verify(triggerIngredientDao).revertTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertTriggerIngredientPropagatesAStaleBaseVersion() {
		when(triggerIngredientDao.revertTriggerIngredient(any(), eq(TRIGGER_INGREDIENT_ID), eq(9L)))
				.thenReturn(Uni.createFrom().failure(new StaleVersionException(null, TRIGGER_INGREDIENT_ID)));

		assertThatThrownBy(() -> newService().revertTriggerIngredient(adminUser(), TRIGGER_INGREDIENT_ID, 9L).await().atMost(AWAIT))
				.isInstanceOf(StaleVersionException.class);
	}

	@Test
	void revertTriggerIngredientRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTriggerIngredient(nonAdminUser(), TRIGGER_INGREDIENT_ID, 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).revertTriggerIngredient(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertRoleOrTechniqueRemovesTheStagedEditForAnAdmin() {
		when(roleOrTechniqueDao.revertRoleOrTechnique(any(), eq(ROLE_OR_TECHNIQUE_ID), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertRoleOrTechnique(adminUser(), ROLE_OR_TECHNIQUE_ID, 1L).await().atMost(AWAIT);

		verify(roleOrTechniqueDao).revertRoleOrTechnique(any(), eq(ROLE_OR_TECHNIQUE_ID), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertRoleOrTechniqueRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertRoleOrTechnique(nonAdminUser(), ROLE_OR_TECHNIQUE_ID, 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).revertRoleOrTechnique(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void triggerIngredientTranslationsForEditReturnsEffectivePerLanguageForAnAdmin() {
		when(triggerIngredientDao.findTranslationsForEdit(any(), eq(TRIGGER_INGREDIENT_ID))).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.EL, new ReferenceDetails("Βόειο", "Κόκκινο κρέας.", 2L, true),
				RecipeLanguage.LT, new ReferenceDetails(null, null, 0L, false))));

		Map<RecipeLanguage, ReferenceDetails> translations = newService()
				.triggerIngredientTranslationsForEdit(adminUser(), TRIGGER_INGREDIENT_ID).await().atMost(AWAIT);

		assertThat(translations.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails("Βόειο", "Κόκκινο κρέας.", 2L, true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void triggerIngredientTranslationsForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().triggerIngredientTranslationsForEdit(nonAdminUser(), TRIGGER_INGREDIENT_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).findTranslationsForEdit(any(), any());
	}

	@Test
	void stageTriggerIngredientTranslationStagesTheEditForAnAdmin() {
		when(triggerIngredientDao.stageTranslation(any(), eq(TRIGGER_INGREDIENT_ID), eq(RecipeLanguage.EL), eq("Βόειο"), eq("Κόκκινο κρέας."), eq(0L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageTriggerIngredientTranslation(adminUser(), TRIGGER_INGREDIENT_ID, RecipeLanguage.EL, "Βόειο", "Κόκκινο κρέας.", 0L).await().atMost(AWAIT);

		verify(triggerIngredientDao).stageTranslation(any(), eq(TRIGGER_INGREDIENT_ID), eq(RecipeLanguage.EL), eq("Βόειο"), eq("Κόκκινο κρέας."), eq(0L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageTriggerIngredientTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageTriggerIngredientTranslation(adminUser(), TRIGGER_INGREDIENT_ID, RecipeLanguage.EN, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(triggerIngredientDao, never()).stageTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageTriggerIngredientTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageTriggerIngredientTranslation(nonAdminUser(), TRIGGER_INGREDIENT_ID, RecipeLanguage.EL, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).stageTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTriggerIngredientTranslationRemovesTheStagedTranslationForAnAdmin() {
		when(triggerIngredientDao.revertTranslation(any(), eq(TRIGGER_INGREDIENT_ID), eq(RecipeLanguage.NL), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertTriggerIngredientTranslation(adminUser(), TRIGGER_INGREDIENT_ID, RecipeLanguage.NL, 1L).await().atMost(AWAIT);

		verify(triggerIngredientDao).revertTranslation(any(), eq(TRIGGER_INGREDIENT_ID), eq(RecipeLanguage.NL), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertTriggerIngredientTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTriggerIngredientTranslation(adminUser(), TRIGGER_INGREDIENT_ID, RecipeLanguage.EN, 1L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(triggerIngredientDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertTriggerIngredientTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertTriggerIngredientTranslation(nonAdminUser(), TRIGGER_INGREDIENT_ID, RecipeLanguage.NL, 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void roleOrTechniqueTranslationsForEditReturnsEffectivePerLanguageForAnAdmin() {
		when(roleOrTechniqueDao.findTranslationsForEdit(any(), eq(ROLE_OR_TECHNIQUE_ID))).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.EL, new ReferenceDetails("ανάμεικτο", "Το κύριο συστατικό.", 3L, true),
				RecipeLanguage.NL, new ReferenceDetails(null, null, 0L, false))));

		Map<RecipeLanguage, ReferenceDetails> translations = newService()
				.roleOrTechniqueTranslationsForEdit(adminUser(), ROLE_OR_TECHNIQUE_ID).await().atMost(AWAIT);

		assertThat(translations.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails("ανάμεικτο", "Το κύριο συστατικό.", 3L, true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void roleOrTechniqueTranslationsForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().roleOrTechniqueTranslationsForEdit(nonAdminUser(), ROLE_OR_TECHNIQUE_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).findTranslationsForEdit(any(), any());
	}

	@Test
	void stageRoleOrTechniqueTranslationStagesTheEditForAnAdmin() {
		when(roleOrTechniqueDao.stageTranslation(any(), eq(ROLE_OR_TECHNIQUE_ID), eq(RecipeLanguage.NL), eq("vermengd"), eq("Het belangrijkste ingrediënt."), eq(0L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageRoleOrTechniqueTranslation(adminUser(), ROLE_OR_TECHNIQUE_ID, RecipeLanguage.NL, "vermengd", "Het belangrijkste ingrediënt.", 0L).await().atMost(AWAIT);

		verify(roleOrTechniqueDao).stageTranslation(any(), eq(ROLE_OR_TECHNIQUE_ID), eq(RecipeLanguage.NL), eq("vermengd"), eq("Het belangrijkste ingrediënt."), eq(0L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageRoleOrTechniqueTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageRoleOrTechniqueTranslation(adminUser(), ROLE_OR_TECHNIQUE_ID, RecipeLanguage.EN, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(roleOrTechniqueDao, never()).stageTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageRoleOrTechniqueTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageRoleOrTechniqueTranslation(nonAdminUser(), ROLE_OR_TECHNIQUE_ID, RecipeLanguage.NL, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).stageTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertRoleOrTechniqueTranslationRemovesTheStagedTranslationForAnAdmin() {
		when(roleOrTechniqueDao.revertTranslation(any(), eq(ROLE_OR_TECHNIQUE_ID), eq(RecipeLanguage.EL), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertRoleOrTechniqueTranslation(adminUser(), ROLE_OR_TECHNIQUE_ID, RecipeLanguage.EL, 1L).await().atMost(AWAIT);

		verify(roleOrTechniqueDao).revertTranslation(any(), eq(ROLE_OR_TECHNIQUE_ID), eq(RecipeLanguage.EL), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertRoleOrTechniqueTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertRoleOrTechniqueTranslation(adminUser(), ROLE_OR_TECHNIQUE_ID, RecipeLanguage.EN, 1L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(roleOrTechniqueDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertRoleOrTechniqueTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertRoleOrTechniqueTranslation(nonAdminUser(), ROLE_OR_TECHNIQUE_ID, RecipeLanguage.EL, 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void createTriggerIngredientStagesANewEntryForAnAdmin() {
		when(triggerIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of()));
		when(triggerIngredientDao.createTriggerIngredient(any(), eq("Quinoa flour"))).thenReturn(Uni.createFrom().item(TRIGGER_INGREDIENT_ID));

		ReferenceOption created = newService().createTriggerIngredient(adminUser(), "Quinoa flour").await().atMost(AWAIT);

		assertThat(created).isEqualTo(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Quinoa flour"));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void createTriggerIngredientRejectsADuplicateNameCaseInsensitivelyWithoutCreating() {
		when(triggerIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(TRIGGER_INGREDIENT_ID, "Soy sauce"))));

		assertThatThrownBy(() -> newService().createTriggerIngredient(adminUser(), "soy sauce").await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(triggerIngredientDao, never()).createTriggerIngredient(any(), any());
	}

	@Test
	void createTriggerIngredientRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().createTriggerIngredient(nonAdminUser(), "Quinoa flour").await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(triggerIngredientDao, never()).listOptions(any());
		verify(triggerIngredientDao, never()).createTriggerIngredient(any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void createRoleOrTechniqueStagesANewEntryForAnAdmin() {
		when(roleOrTechniqueDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of()));
		when(roleOrTechniqueDao.createRoleOrTechnique(any(), eq("Binding agent"))).thenReturn(Uni.createFrom().item(ROLE_OR_TECHNIQUE_ID));

		ReferenceOption created = newService().createRoleOrTechnique(adminUser(), "Binding agent").await().atMost(AWAIT);

		assertThat(created).isEqualTo(new ReferenceOption(ROLE_OR_TECHNIQUE_ID, "Binding agent"));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void createRoleOrTechniqueRejectsADuplicateNameCaseInsensitivelyWithoutCreating() {
		when(roleOrTechniqueDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(ROLE_OR_TECHNIQUE_ID, "seasoning"))));

		assertThatThrownBy(() -> newService().createRoleOrTechnique(adminUser(), "Seasoning").await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(roleOrTechniqueDao, never()).createRoleOrTechnique(any(), any());
	}

	@Test
	void createRoleOrTechniqueRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().createRoleOrTechnique(nonAdminUser(), "Binding agent").await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(roleOrTechniqueDao, never()).listOptions(any());
		verify(roleOrTechniqueDao, never()).createRoleOrTechnique(any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void createAlternativeIngredientStagesANewEntryForAnAdmin() {
		when(alternativeIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of()));
		when(alternativeIngredientDao.createAlternativeIngredient(any(), eq("Aquafaba"))).thenReturn(Uni.createFrom().item(ALTERNATIVE_INGREDIENT_ID));

		ReferenceOption created = newService().createAlternativeIngredient(adminUser(), "Aquafaba").await().atMost(AWAIT);

		assertThat(created).isEqualTo(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Aquafaba"));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void createAlternativeIngredientRejectsADuplicateNameCaseInsensitivelyWithoutCreating() {
		when(alternativeIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Smoked tofu cubes"))));

		assertThatThrownBy(() -> newService().createAlternativeIngredient(adminUser(), "smoked tofu cubes").await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(alternativeIngredientDao, never()).createAlternativeIngredient(any(), any());
	}

	@Test
	void createAlternativeIngredientRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().createAlternativeIngredient(nonAdminUser(), "Aquafaba").await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).listOptions(any());
		verify(alternativeIngredientDao, never()).createAlternativeIngredient(any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void alternativeIngredientForEditReturnsEffectiveDetailsAndBlastRadiusForAnAdmin() {
		when(alternativeIngredientDao.findEditableById(any(), eq(ALTERNATIVE_INGREDIENT_ID)))
				.thenReturn(Uni.createFrom().item(new ReferenceDetails("Smoked tofu cubes", "Pressed and smoked.", 2L, true)));
		when(suggestionTemplateDao.countTemplatesByAlternative(any(), eq(ALTERNATIVE_INGREDIENT_ID)))
				.thenReturn(Uni.createFrom().item(4L));

		AlternativeIngredientForEdit forEdit = newService()
				.alternativeIngredientForEdit(adminUser(), ALTERNATIVE_INGREDIENT_ID).await().atMost(AWAIT);

		assertThat(forEdit).isEqualTo(new AlternativeIngredientForEdit("Smoked tofu cubes", "Pressed and smoked.", 2L, true, 4L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void alternativeIngredientForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().alternativeIngredientForEdit(nonAdminUser(), ALTERNATIVE_INGREDIENT_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).findEditableById(any(), any());
		verify(suggestionTemplateDao, never()).countTemplatesByAlternative(any(), any());
	}

	@Test
	void editAlternativeIngredientStagesTheEditForAnAdmin() {
		when(alternativeIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Smoked tofu cubes"))));
		when(alternativeIngredientDao.editAlternativeIngredient(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq("Smoked tofu"), eq("Pressed and smoked."), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().editAlternativeIngredient(adminUser(), ALTERNATIVE_INGREDIENT_ID, "Smoked tofu", "Pressed and smoked.", 1L).await().atMost(AWAIT);

		verify(alternativeIngredientDao).editAlternativeIngredient(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq("Smoked tofu"), eq("Pressed and smoked."), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void editAlternativeIngredientRejectsRenamingToAnotherEntrysNameCaseInsensitively() {
		when(alternativeIngredientDao.listOptions(any())).thenReturn(Uni.createFrom().item(List.of(
				new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Smoked tofu cubes"),
				new ReferenceOption(NEW_TEMPLATE_ID, "Aquafaba"))));

		assertThatThrownBy(() -> newService().editAlternativeIngredient(adminUser(), ALTERNATIVE_INGREDIENT_ID, "aquafaba", "x", 1L).await().atMost(AWAIT))
				.isInstanceOf(DuplicateBusinessKeyException.class);
		verify(alternativeIngredientDao, never()).editAlternativeIngredient(any(), any(), any(), any(), anyLong());
	}

	@Test
	void editAlternativeIngredientRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().editAlternativeIngredient(nonAdminUser(), ALTERNATIVE_INGREDIENT_ID, "Smoked tofu", "x", 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).listOptions(any());
		verify(alternativeIngredientDao, never()).editAlternativeIngredient(any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertAlternativeIngredientRemovesTheStagedEditForAnAdmin() {
		when(alternativeIngredientDao.revertAlternativeIngredient(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertAlternativeIngredient(adminUser(), ALTERNATIVE_INGREDIENT_ID, 1L).await().atMost(AWAIT);

		verify(alternativeIngredientDao).revertAlternativeIngredient(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertAlternativeIngredientRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertAlternativeIngredient(nonAdminUser(), ALTERNATIVE_INGREDIENT_ID, 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).revertAlternativeIngredient(any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void alternativeIngredientTranslationsForEditReturnsEffectivePerLanguageForAnAdmin() {
		when(alternativeIngredientDao.findTranslationsForEdit(any(), eq(ALTERNATIVE_INGREDIENT_ID))).thenReturn(Uni.createFrom().item(Map.of(
				RecipeLanguage.EL, new ReferenceDetails("Καπνιστό τόφου", "Πρεσαριστό.", 2L, true),
				RecipeLanguage.LT, new ReferenceDetails(null, null, 0L, false))));

		Map<RecipeLanguage, ReferenceDetails> translations = newService()
				.alternativeIngredientTranslationsForEdit(adminUser(), ALTERNATIVE_INGREDIENT_ID).await().atMost(AWAIT);

		assertThat(translations.get(RecipeLanguage.EL)).isEqualTo(new ReferenceDetails("Καπνιστό τόφου", "Πρεσαριστό.", 2L, true));
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void alternativeIngredientTranslationsForEditRejectsANonAdmin() {
		assertThatThrownBy(() -> newService().alternativeIngredientTranslationsForEdit(nonAdminUser(), ALTERNATIVE_INGREDIENT_ID).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).findTranslationsForEdit(any(), any());
	}

	@Test
	void stageAlternativeIngredientTranslationStagesTheEditForAnAdmin() {
		when(alternativeIngredientDao.stageTranslation(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq(RecipeLanguage.EL), eq("Καπνιστό τόφου"), eq("Πρεσαριστό."), eq(0L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().stageAlternativeIngredientTranslation(adminUser(), ALTERNATIVE_INGREDIENT_ID, RecipeLanguage.EL, "Καπνιστό τόφου", "Πρεσαριστό.", 0L).await().atMost(AWAIT);

		verify(alternativeIngredientDao).stageTranslation(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq(RecipeLanguage.EL), eq("Καπνιστό τόφου"), eq("Πρεσαριστό."), eq(0L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void stageAlternativeIngredientTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageAlternativeIngredientTranslation(adminUser(), ALTERNATIVE_INGREDIENT_ID, RecipeLanguage.EN, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(alternativeIngredientDao, never()).stageTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void stageAlternativeIngredientTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().stageAlternativeIngredientTranslation(nonAdminUser(), ALTERNATIVE_INGREDIENT_ID, RecipeLanguage.EL, "x", "y", 0L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).stageTranslation(any(), any(), any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertAlternativeIngredientTranslationRemovesTheStagedTranslationForAnAdmin() {
		when(alternativeIngredientDao.revertTranslation(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq(RecipeLanguage.NL), eq(1L)))
				.thenReturn(Uni.createFrom().voidItem());

		newService().revertAlternativeIngredientTranslation(adminUser(), ALTERNATIVE_INGREDIENT_ID, RecipeLanguage.NL, 1L).await().atMost(AWAIT);

		verify(alternativeIngredientDao).revertTranslation(any(), eq(ALTERNATIVE_INGREDIENT_ID), eq(RecipeLanguage.NL), eq(1L));
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
	}

	@Test
	void revertAlternativeIngredientTranslationRejectsEnglishWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertAlternativeIngredientTranslation(adminUser(), ALTERNATIVE_INGREDIENT_ID, RecipeLanguage.EN, 1L).await().atMost(AWAIT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(alternativeIngredientDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@Test
	void revertAlternativeIngredientTranslationRejectsANonAdminWithoutOpeningATransaction() {
		assertThatThrownBy(() -> newService().revertAlternativeIngredientTranslation(nonAdminUser(), ALTERNATIVE_INGREDIENT_ID, RecipeLanguage.NL, 1L).await().atMost(AWAIT))
				.isInstanceOf(NotAuthorizedException.class);
		verify(alternativeIngredientDao, never()).revertTranslation(any(), any(), any(), anyLong());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private BackofficeReferenceDataServiceImpl newService() {
		return new BackofficeReferenceDataServiceImpl(triggerIngredientDao, roleOrTechniqueDao, alternativeIngredientDao, suggestionTemplateDao, persistenceContextFactory, new AuthorizationImpl());
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
