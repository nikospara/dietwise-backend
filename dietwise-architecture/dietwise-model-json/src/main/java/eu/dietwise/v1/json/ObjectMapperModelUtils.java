package eu.dietwise.v1.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.AppliesToMixin;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.PersonalInfoMixin;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeAssessmentParamMixin;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParamMixin;

public interface ObjectMapperModelUtils {
	static ObjectMapper applyDefaultObjectMapperConfiguration(ObjectMapper om) {
		// Let's keep them ordered alphabetically for sanity
		om.addMixIn(AppliesTo.class, AppliesToMixin.class);
		om.addMixIn(PersonalInfo.class, PersonalInfoMixin.class);
		om.addMixIn(RecipeAssessmentParam.class, RecipeAssessmentParamMixin.class);
		om.addMixIn(RecipeExtractionAndAssessmentParam.class, RecipeExtractionAndAssessmentParamMixin.class);
		return om;
	}
}
