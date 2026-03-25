package com.jorisjonkers.privatestack.common.web

import java.net.URI

data class ProblemDetail(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: URI? = null,
    val errors: List<FieldError> = emptyList(),
)

data class FieldError(
    val field: String,
    val message: String,
)
