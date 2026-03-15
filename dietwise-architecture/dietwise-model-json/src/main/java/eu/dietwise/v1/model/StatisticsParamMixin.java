package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutableStatisticsParam.Builder.class)
public abstract class StatisticsParamMixin {
}
