# agent-runner: the image every per-workspace Pod (and the auth
# bootstrap + refresh ping) boots from. Bundles:
#   - Claude Code CLI + Codex CLI (the unit of "the agent")
#   - tmux + bash + git + gh (the agent's tools)
#   - language toolchains light enough to share one image (node, jdk,
#     python, go); per-workspace specialisation happens via mise/asdf
#     inside the workspace later
#   - the agent-gateway jar as PID 1, owning the tmux server and the
#     REST/WS plane assistant-api talks to
#
# The image is deliberately mutable inside the container; adversarial
# use is in scope. Isolation comes from the Pod boundary + the
# `agent-runners` NetworkPolicy + the credential PVCs being mounted
# only where they are needed.

FROM gradle:9.4-jdk21-alpine AS gateway-build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY libs/kotlin-common/build.gradle.kts libs/kotlin-common/
COPY services/auth-api/build.gradle.kts services/auth-api/
COPY services/assistant-api/build.gradle.kts services/assistant-api/
COPY services/knowledge-api/build.gradle.kts services/knowledge-api/
COPY services/agent-gateway/build.gradle.kts services/agent-gateway/
COPY services/system-tests/build.gradle.kts services/system-tests/
RUN gradle :services:agent-gateway:dependencies --no-daemon || true
COPY libs/kotlin-common/src/main/ libs/kotlin-common/src/main/
COPY services/agent-gateway/src/main/ services/agent-gateway/src/main/
RUN gradle :services:agent-gateway:bootJar --no-daemon

FROM debian:bookworm-slim
ARG GIT_SHA=unknown
ENV SERVICE_VERSION=${GIT_SHA}
ENV DEBIAN_FRONTEND=noninteractive

# Base tools. `gh` from the official apt source; node 22 LTS via the
# nodesource one-liner; Temurin JDK 21 from the Adoptium apt source;
# python from Debian.
#
# Java must be 21, not Debian bookworm's `default-jdk` — that resolves
# to openjdk-17, and the gateway jar is compiled to Java 21 bytecode
# (class file 65.0), so a 17 runtime dies at boot with
# `UnsupportedClassVersionError ... only ... up to 61.0` and every
# runner Pod crash-loops. openjdk-21 is in neither bookworm main nor
# backports, so the Adoptium Temurin build is the source.
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl gnupg jq tini \
      bash zsh tmux git openssh-client \
      build-essential \
      python3 python3-pip python3-venv \
      ripgrep fd-find bat less \
      bubblewrap \
    && curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
       | gpg --dearmor -o /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
       > /etc/apt/sources.list.d/github-cli.list \
    && curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
       | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" \
       > /etc/apt/sources.list.d/adoptium.list \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends gh nodejs temurin-21-jdk \
    && rm -rf /var/lib/apt/lists/*

# Claude Code + Codex CLIs. Both ship via npm; pin to the versions
# the auth flow was tested against and bump deliberately. The CLIs
# read OAuth tokens from $HOME/.claude/.credentials.json and
# $CODEX_HOME/auth.json — both mounted as PVCs by every Pod that
# uses this image.
RUN npm install -g \
      @anthropic-ai/claude-code \
      @openai/codex \
    && npm cache clean --force

# The gateway jar runs on the Temurin 21 JDK installed above; the
# entrypoint launches it with ZGC the same way every other service does.
COPY --from=gateway-build /app/services/agent-gateway/build/libs/*.jar /opt/agent-gateway.jar
COPY services/agent-runner/entrypoint.sh /opt/entrypoint.sh
RUN chmod +x /opt/entrypoint.sh

# Non-root agent user, with /workspace writable. /home/agent is the
# anchor for ~/.claude, ~/.codex, ~/.gitconfig, ~/.ssh — all mounted
# from PVCs/Secrets at runtime.
RUN useradd -m -u 1000 -s /bin/bash agent \
    && mkdir -p /workspace /home/agent/.claude /home/agent/.codex /home/agent/.ssh \
    && chown -R agent:agent /workspace /home/agent
USER agent
WORKDIR /workspace
ENV HOME=/home/agent
ENV CODEX_HOME=/home/agent/.codex
ENV PATH=/home/agent/.npm-global/bin:$PATH

EXPOSE 8090
ENTRYPOINT ["/usr/bin/tini", "--", "/opt/entrypoint.sh"]
