package eu.dietwise.v1.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeAssessmentParamMixin;

public interface ObjectMapperModelUtils {
	static ObjectMapper applyDefaultObjectMapperConfiguration(ObjectMapper om) {
		om.addMixIn(RecipeAssessmentParam.class, RecipeAssessmentParamMixin.class);
		return om;
	}
}
