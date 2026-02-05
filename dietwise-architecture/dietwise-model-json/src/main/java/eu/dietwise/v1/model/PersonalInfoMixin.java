package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = ImmutablePersonalInfo.Builder.class)
public class PersonalInfoMixin {
}
