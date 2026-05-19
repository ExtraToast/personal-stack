package com.jorisjonkers.personalstack.assistant.domain.model

import java.util.UUID

@JvmInline
value class WorkspaceId(val value: UUID) {
    override fun toString(): String = value.toString()

    fun short(): String = value.toString().substring(0, 8)

    companion object {
        fun random(): WorkspaceId = WorkspaceId(UUID.randomUUID())

        fun parse(s: String): WorkspaceId = WorkspaceId(UUID.fromString(s))
    }
}
