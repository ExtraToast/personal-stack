package com.jorisjonkers.personalstack.auth.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class AuthenticatedUserMixin
    @JsonCreator
    constructor(
        @JsonProperty("userId") userId: UserId,
        @JsonProperty("username") username: String,
        @JsonProperty("roles") roles: List<String>,
        @JsonProperty("passwordHash") passwordHash: String,
    )

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
abstract class UserIdMixin
    @JsonCreator
    constructor(
        @JsonProperty("value") value: UUID,
    )
