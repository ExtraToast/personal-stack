FROM node:26-alpine AS build
# Node 25's alpine image dropped corepack from the base layer but still
# ships /usr/local/bin/yarn, which makes `npm install -g corepack` fail
# with EEXIST. Install pnpm directly at the packageManager version in
# package.json instead of going through corepack's shim dance.
RUN npm install -g pnpm@9.15.4
WORKDIR /app
ARG NODE_AUTH_TOKEN=
COPY .npmrc pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY services/assistant-ui/package.json services/assistant-ui/
RUN --mount=type=secret,id=github_token \
    NODE_AUTH_TOKEN="${NODE_AUTH_TOKEN:-$(cat /run/secrets/github_token 2>/dev/null || true)}" \
    pnpm install --frozen-lockfile
COPY services/assistant-ui/ services/assistant-ui/
ARG VITE_AUTH_URL=https://auth.jorisjonkers.dev
ARG VITE_FARO_URL=https://faro.jorisjonkers.dev/collect
ARG VITE_GITHUB_APP_SLUG=personal-stack-agents
RUN VITE_AUTH_URL=${VITE_AUTH_URL} \
    VITE_FARO_URL=${VITE_FARO_URL} \
    VITE_GITHUB_APP_SLUG=${VITE_GITHUB_APP_SLUG} \
    pnpm --filter @personal-stack/assistant-ui build

FROM nginx:alpine
COPY services/assistant-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/services/assistant-ui/dist /usr/share/nginx/html
EXPOSE 80
