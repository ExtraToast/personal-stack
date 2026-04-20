FROM node:25-alpine AS build
# Node 25's alpine image no longer ships corepack in the base layer
# (the experimental tool was pulled from upstream distribution), so
# install it explicitly before activating pnpm.
RUN npm install -g corepack@latest && corepack enable pnpm
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY libs/vue-common/package.json libs/vue-common/
COPY services/auth-ui/package.json services/auth-ui/
RUN pnpm install --frozen-lockfile
COPY libs/vue-common/ libs/vue-common/
COPY services/auth-ui/ services/auth-ui/
RUN pnpm --filter @personal-stack/auth-ui build

FROM nginx:alpine
COPY services/auth-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/services/auth-ui/dist /usr/share/nginx/html
EXPOSE 80
