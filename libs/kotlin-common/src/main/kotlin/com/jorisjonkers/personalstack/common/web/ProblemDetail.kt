package com.jorisjonkers.personalstack.common.web

import com.fasterxml.jackson.annotation.JsonInclude
import java.net.URI

/**
 * RFC 7807-flavoured problem payload. The base fields (`type`,
 * `title`, `status`, `detail`, `instance`) match the standard; the
 * remaining fields are extension members that carry context
 * specific to this stack (validation violations, traceId, the
 * upstream Kubernetes API server's verdict, …).
 *
 * Empty/null extension fields are stripped from the JSON so the
 * baseline RFC 7807 payload stays minimal for callers that ignore
 * the extensions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ProblemDetail(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: URI? = null,
    val errors: List<FieldError> = emptyList(),
    /** Correlation id from MDC, set by the upstream tracing filter. */
    val traceId: String? = null,
    /** Exception class name — populated for unexpected 5xx so the
     * support workflow can grep logs without guessing. */
    val exception: String? = null,
    /** Kubernetes API server's numeric status code on a 502. */
    val kubernetesCode: Int? = null,
    /** Kubernetes API server's `Status.reason` on a 502 (e.g.
     * `Forbidden`, `Invalid`, `NotFound`). */
    val kubernetesReason: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null,
)
