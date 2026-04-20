FROM node:25-alpine AS build
# Node 25's alpine image dropped corepack from the base layer but still
# ships /usr/local/bin/yarn, which makes `npm install -g corepack` fail
# with EEXIST. Install pnpm directly at the packageManager version in
# package.json instead of going through corepack's shim dance.
RUN npm install -g pnpm@9.15.4
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
