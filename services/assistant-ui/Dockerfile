FROM node:20-alpine AS build
RUN corepack enable pnpm
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY libs/vue-common/package.json libs/vue-common/
COPY services/assistant-ui/package.json services/assistant-ui/
RUN pnpm install --frozen-lockfile
COPY libs/vue-common/ libs/vue-common/
COPY services/assistant-ui/ services/assistant-ui/
ARG VITE_AUTH_URL=https://auth.jorisjonkers.dev
ENV VITE_AUTH_URL=$VITE_AUTH_URL
RUN pnpm --filter @private-stack/assistant-ui build

FROM nginx:alpine
COPY services/assistant-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/services/assistant-ui/dist /usr/share/nginx/html
EXPOSE 80
