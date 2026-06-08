FROM node:26-alpine AS build
# Node 25's alpine image dropped corepack from the base layer but still
# ships /usr/local/bin/yarn, which makes `npm install -g corepack` fail
# with EEXIST. Install pnpm directly at the packageManager version in
# package.json instead of going through corepack's shim dance.
RUN npm install -g pnpm@9.15.4
WORKDIR /app
ARG NODE_AUTH_TOKEN=
COPY .npmrc pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY services/auth-ui/package.json services/auth-ui/
RUN --mount=type=secret,id=github_token \
    NODE_AUTH_TOKEN="${NODE_AUTH_TOKEN:-$(cat /run/secrets/github_token 2>/dev/null || true)}" \
    pnpm install --frozen-lockfile
COPY services/auth-ui/ services/auth-ui/
ARG VITE_FARO_URL=https://faro.jorisjonkers.dev/collect
RUN VITE_FARO_URL=${VITE_FARO_URL} pnpm --filter @personal-stack/auth-ui build

FROM nginx:alpine
COPY services/auth-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/services/auth-ui/dist /usr/share/nginx/html
EXPOSE 80
