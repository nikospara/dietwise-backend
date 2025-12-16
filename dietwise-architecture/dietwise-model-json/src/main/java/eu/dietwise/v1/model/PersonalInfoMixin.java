package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = ImmutablePersonalInfo.class)
public class PersonalInfoMixin {
}
