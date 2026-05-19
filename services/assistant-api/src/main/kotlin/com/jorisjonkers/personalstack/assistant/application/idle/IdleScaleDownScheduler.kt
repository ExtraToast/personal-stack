package com.jorisjonkers.personalstack.assistant.application.idle

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentRunnerOrchestrator
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Periodic sweep: any READY workspace whose last activity is older
 * than `agent-runtime.idle-after` gets its Pod + Service + Secret
 * torn down. The PVC (workspace volume) survives so a follow-up
 * "wake" operation can re-provision the Pod with the same disk —
 * the future operation isn't implemented yet, but the destroy step
 * is the part that costs cluster resources and is worth doing now.
 *
 * Defaults: idle threshold 30 min, sweep every 5 min. Both
 * overridable via env so a noisier or quieter cluster can tune.
 */
@Component
class IdleScaleDownScheduler(
    private val workspaces: WorkspaceRepository,
    private val orchestrator: AgentRunnerOrchestrator,
    private val tracker: WorkspaceActivityTracker,
    private val clock: Clock = Clock.systemUTC(),
    @param:Value("\${agent-runtime.idle-after-seconds:1800}")
    private val idleAfterSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(IdleScaleDownScheduler::class.java)

    @Scheduled(fixedDelayString = "\${agent-runtime.idle-sweep-period-ms:300000}")
    fun sweep() {
        val threshold = clock.instant().minus(Duration.ofSeconds(idleAfterSeconds))
        val candidates =
            workspaces
                .findAllByStatusNot(WorkspaceStatus.DESTROYED)
                .filter { it.status == WorkspaceStatus.READY && !effectiveLastSeen(it).isAfter(threshold) }
        candidates.forEach { scaleDown(it) }
        if (candidates.isNotEmpty()) log.info("idle-sweep scaled down {} workspace(s)", candidates.size)
    }

    private fun effectiveLastSeen(workspace: Workspace): Instant = tracker.lastSeen(workspace.id) ?: workspace.updatedAt

    private fun scaleDown(workspace: Workspace) {
        runCatching { orchestrator.destroy(workspace) }
            .onFailure {
                log.warn("scale-down of {} failed: {}", workspace.id, it.message)
                return
            }
        workspaces.save(
            workspace.copy(
                status = WorkspaceStatus.IDLE,
                podName = null,
                gatewayEndpoint = null,
                updatedAt = clock.instant(),
            ),
        )
        tracker.forget(workspace.id)
        log.info("workspace {} idle-scaled to zero (last seen {})", workspace.id, effectiveLastSeen(workspace))
    }
}
