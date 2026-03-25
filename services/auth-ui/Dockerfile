FROM node:20-alpine AS build
RUN corepack enable pnpm
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY libs/vue-common/package.json libs/vue-common/
COPY services/auth-ui/package.json services/auth-ui/
RUN pnpm install --frozen-lockfile
COPY libs/vue-common/ libs/vue-common/
COPY services/auth-ui/ services/auth-ui/
RUN pnpm --filter @private-stack/auth-ui build

FROM nginx:alpine
COPY services/auth-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/services/auth-ui/dist /usr/share/nginx/html
EXPOSE 80
