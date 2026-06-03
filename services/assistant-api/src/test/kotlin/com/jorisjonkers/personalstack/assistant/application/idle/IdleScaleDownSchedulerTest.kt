package com.jorisjonkers.personalstack.assistant.application.idle

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IdleScaleDownSchedulerTest {
    private class FixedClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this

        override fun instant(): Instant = now
    }

    private val now = Instant.parse("2026-05-19T12:00:00Z")
    private val clock = FixedClock(now)
    private val workspaces = mockk<WorkspaceRepository>()
    private val orchestrator = mockk<AgentRunnerOrchestrator>(relaxed = true)
    private val tracker = WorkspaceActivityTracker(clock)
    private val scheduler =
        IdleScaleDownScheduler(
            workspaces = workspaces,
            orchestrator = orchestrator,
            tracker = tracker,
            clock = clock,
            idleAfterSeconds = 1_800,
        )

    @Test
    fun `sweep scales down a READY workspace whose last activity is older than the threshold`() {
        val ws = workspace(updatedAt = now.minusSeconds(7_200))
        every { workspaces.findAllByStatusNot(WorkspaceStatus.DESTROYED) } returns listOf(ws)
        val saved = slot<Workspace>()
        every { workspaces.save(capture(saved)) } answers { saved.captured }

        scheduler.sweep()

        verify { orchestrator.scaleDown(ws) }
        verify(exactly = 0) { orchestrator.destroy(any()) }
        assertThat(saved.captured.status).isEqualTo(WorkspaceStatus.IDLE)
        assertThat(saved.captured.podName).isNull()
        assertThat(saved.captured.gatewayEndpoint).isNull()
    }

    @Test
    fun `sweep leaves recently active workspaces alone`() {
        val ws = workspace(updatedAt = now.minusSeconds(60))
        every { workspaces.findAllByStatusNot(WorkspaceStatus.DESTROYED) } returns listOf(ws)
        tracker.touch(ws.id)

        scheduler.sweep()

        verify(exactly = 0) { orchestrator.scaleDown(any()) }
        verify(exactly = 0) { orchestrator.destroy(any()) }
        verify(exactly = 0) { workspaces.save(any()) }
    }

    @Test
    fun `sweep skips workspaces not in READY status`() {
        val ws = workspace(updatedAt = now.minusSeconds(7_200), status = WorkspaceStatus.PENDING)
        every { workspaces.findAllByStatusNot(WorkspaceStatus.DESTROYED) } returns listOf(ws)

        scheduler.sweep()

        verify(exactly = 0) { orchestrator.scaleDown(any()) }
        verify(exactly = 0) { orchestrator.destroy(any()) }
    }

    @Test
    fun `tracker timestamp wins over workspace updatedAt when fresher`() {
        val ws = workspace(updatedAt = now.minusSeconds(7_200))
        tracker.touch(ws.id) // fresh-as-now
        every { workspaces.findAllByStatusNot(WorkspaceStatus.DESTROYED) } returns listOf(ws)

        scheduler.sweep()

        verify(exactly = 0) { orchestrator.scaleDown(any()) }
        verify(exactly = 0) { orchestrator.destroy(any()) }
    }

    private fun workspace(
        updatedAt: Instant,
        status: WorkspaceStatus = WorkspaceStatus.READY,
    ) = Workspace(
        id = WorkspaceId.random(),
        name = "demo",
        repoUrl = null,
        branch = null,
        podName = "agent-runner-deadbeef",
        pvcName = "workspace-deadbeef",
        gatewayEndpoint = "http://x:8090",
        status = status,
        createdAt = updatedAt.minusSeconds(1),
        updatedAt = updatedAt,
    )
}
