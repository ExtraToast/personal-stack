FROM node:24-alpine AS build
RUN corepack enable pnpm
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY libs/vue-common/package.json libs/vue-common/
COPY services/app-ui/package.json services/app-ui/
RUN pnpm install --frozen-lockfile
COPY libs/vue-common/ libs/vue-common/
COPY services/app-ui/ services/app-ui/
RUN pnpm --filter @private-stack/app-ui build

FROM nginx:alpine
COPY services/app-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/services/app-ui/dist /usr/share/nginx/html
EXPOSE 80
