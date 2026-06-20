package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.SuggestionTemplateDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.v1.BackofficeReferenceDataService;
import eu.dietwise.services.v1.types.AlternativeIngredientForEdit;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class BackofficeReferenceDataServiceImpl implements BackofficeReferenceDataService {
	private final TriggerIngredientDao triggerIngredientDao;
	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final AlternativeIngredientDao alternativeIngredientDao;
	private final SuggestionTemplateDao suggestionTemplateDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public BackofficeReferenceDataServiceImpl(
			TriggerIngredientDao triggerIngredientDao,
			RoleOrTechniqueDao roleOrTechniqueDao,
			AlternativeIngredientDao alternativeIngredientDao,
			SuggestionTemplateDao suggestionTemplateDao,
			ReactivePersistenceContextFactory persistenceContextFactory,
			Authorization authorization
	) {
		this.triggerIngredientDao = triggerIngredientDao;
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.suggestionTemplateDao = suggestionTemplateDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<ReferenceOption> createTriggerIngredient(User user, String name) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.listOptions(tx).flatMap(options -> nameExists(options, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Trigger Ingredient named '" + name + "' already exists"))
				: triggerIngredientDao.createTriggerIngredient(tx, name).map(id -> new ReferenceOption(id, name))));
	}

	@Override
	public Uni<ReferenceDetails> triggerIngredientForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> triggerIngredientDao.findEditableById(em, id));
	}

	@Override
	public Uni<Void> editTriggerIngredient(User user, UUID id, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.listOptions(tx).flatMap(options -> nameTaken(options, id, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Trigger Ingredient named '" + name + "' already exists"))
				: triggerIngredientDao.editTriggerIngredient(tx, id, name, explanationForLlm, baseVersion)));
	}

	@Override
	public Uni<Void> revertTriggerIngredient(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.revertTriggerIngredient(tx, id, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> triggerIngredientTranslationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> triggerIngredientDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageTriggerIngredientTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.stageTranslation(tx, id, lang, name, explanationForLlm, baseVersion));
	}

	@Override
	public Uni<Void> revertTriggerIngredientTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> triggerIngredientDao.revertTranslation(tx, id, lang, baseVersion));
	}

	@Override
	public Uni<ReferenceOption> createRoleOrTechnique(User user, String name) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.listOptions(tx).flatMap(options -> nameExists(options, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Role or Technique named '" + name + "' already exists"))
				: roleOrTechniqueDao.createRoleOrTechnique(tx, name).map(id -> new ReferenceOption(id, name))));
	}

	@Override
	public Uni<ReferenceDetails> roleOrTechniqueForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> roleOrTechniqueDao.findEditableById(em, id));
	}

	@Override
	public Uni<Void> editRoleOrTechnique(User user, UUID id, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.listOptions(tx).flatMap(options -> nameTaken(options, id, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("A Role or Technique named '" + name + "' already exists"))
				: roleOrTechniqueDao.editRoleOrTechnique(tx, id, name, explanationForLlm, baseVersion)));
	}

	@Override
	public Uni<Void> revertRoleOrTechnique(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.revertRoleOrTechnique(tx, id, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> roleOrTechniqueTranslationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> roleOrTechniqueDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageRoleOrTechniqueTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.stageTranslation(tx, id, lang, name, explanationForLlm, baseVersion));
	}

	@Override
	public Uni<Void> revertRoleOrTechniqueTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> roleOrTechniqueDao.revertTranslation(tx, id, lang, baseVersion));
	}

	@Override
	public Uni<ReferenceOption> createAlternativeIngredient(User user, String name) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.listOptions(tx).flatMap(options -> nameExists(options, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("An Alternative Ingredient named '" + name + "' already exists"))
				: alternativeIngredientDao.createAlternativeIngredient(tx, name).map(id -> new ReferenceOption(id, name))));
	}

	@Override
	public Uni<AlternativeIngredientForEdit> alternativeIngredientForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> forcm(
				alternativeIngredientDao.findEditableById(em, id),
				_ -> suggestionTemplateDao.countTemplatesByAlternative(em, id),
				(details, count) -> new AlternativeIngredientForEdit(details.name(), details.explanationForLlm(), details.version(), details.published(), count)
		));
	}

	@Override
	public Uni<Void> editAlternativeIngredient(User user, UUID id, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.listOptions(tx).flatMap(options -> nameTaken(options, id, name)
				? Uni.createFrom().failure(new DuplicateBusinessKeyException("An Alternative Ingredient named '" + name + "' already exists"))
				: alternativeIngredientDao.editAlternativeIngredient(tx, id, name, explanationForLlm, baseVersion)));
	}

	@Override
	public Uni<Void> revertAlternativeIngredient(User user, UUID id, long baseVersion) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.revertAlternativeIngredient(tx, id, baseVersion));
	}

	@Override
	public Uni<Map<RecipeLanguage, ReferenceDetails>> alternativeIngredientTranslationsForEdit(User user, UUID id) {
		authorization.requireAdmin(user);
		return persistenceContextFactory.withoutTransaction(em -> alternativeIngredientDao.findTranslationsForEdit(em, id));
	}

	@Override
	public Uni<Void> stageAlternativeIngredientTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.stageTranslation(tx, id, lang, name, explanationForLlm, baseVersion));
	}

	@Override
	public Uni<Void> revertAlternativeIngredientTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion) {
		authorization.requireAdmin(user);
		BackofficeTranslations.requireTranslatableLanguage(lang);
		return persistenceContextFactory.withTransaction(tx -> alternativeIngredientDao.revertTranslation(tx, id, lang, baseVersion));
	}

	private static boolean nameExists(List<ReferenceOption> options, String name) {
		return options.stream().anyMatch(option -> option.name().equalsIgnoreCase(name));
	}

	private static boolean nameTaken(List<ReferenceOption> options, UUID id, String name) {
		return options.stream().anyMatch(option -> !option.id().equals(id) && option.name().equalsIgnoreCase(name));
	}
}
