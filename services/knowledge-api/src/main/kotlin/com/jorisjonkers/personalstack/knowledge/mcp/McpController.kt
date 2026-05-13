package com.jorisjonkers.personalstack.knowledge.mcp

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Streamable-HTTP MCP transport. Phase 4b ships only the envelope and
 * the three handshake methods — `initialize`, `ping`, and an empty
 * `tools/list`. Phase 4c adds the real tools (recall / capture_lesson
 * / capture_decision / etc.) by extending [tools] and the
 * `tools/call` branch.
 *
 * Bearer auth runs as a Spring filter before this controller sees the
 * request; the resolved token name lives on the request attribute
 * `knowledge.mcp.user` (`mcp:<name>`) for logging.
 */
@RestController
@RequestMapping("/mcp", produces = [MediaType.APPLICATION_JSON_VALUE])
class McpController {
    private val log = LoggerFactory.getLogger(McpController::class.java)

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun rpc(
        @RequestBody request: JsonRpcRequest,
        servletRequest: HttpServletRequest,
    ): JsonRpcResponse {
        if (request.jsonrpc != "2.0") {
            return JsonRpcResponse(
                id = request.id,
                error =
                    JsonRpcError(
                        code = JsonRpcErrorCodes.INVALID_REQUEST,
                        message = "jsonrpc field must be \"2.0\"",
                    ),
            )
        }

        return try {
            when (request.method) {
                "initialize" -> JsonRpcResponse(id = request.id, result = handleInitialize())
                "ping" -> JsonRpcResponse(id = request.id, result = emptyMap<String, Any>())
                "tools/list" -> JsonRpcResponse(id = request.id, result = mapOf("tools" to emptyList<Any>()))
                else ->
                    JsonRpcResponse(
                        id = request.id,
                        error =
                            JsonRpcError(
                                code = JsonRpcErrorCodes.METHOD_NOT_FOUND,
                                message = "method '${request.method}' not implemented",
                            ),
                    )
            }
        } catch (ex: Exception) {
            log.error("MCP RPC handler failed for method={}", request.method, ex)
            JsonRpcResponse(
                id = request.id,
                error =
                    JsonRpcError(
                        code = JsonRpcErrorCodes.INTERNAL_ERROR,
                        message = "internal error handling ${request.method}",
                    ),
            )
        }
    }

    private fun handleInitialize(): Map<String, Any> =
        mapOf(
            "protocolVersion" to PROTOCOL_VERSION,
            "serverInfo" to
                mapOf(
                    "name" to "knowledge-api",
                    "version" to (System.getenv("SERVICE_VERSION") ?: "unknown"),
                ),
            "capabilities" to
                mapOf(
                    "tools" to mapOf("listChanged" to false),
                ),
        )

    companion object {
        private const val PROTOCOL_VERSION = "2025-06-18"
    }
}
