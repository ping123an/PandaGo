# ========== PandaGo 前端镜像（多阶段构建） ==========
# 阶段一：Node 22 构建静态资源
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm ci || npm install

COPY . .
# esbuild 在部分环境下需要可写临时目录，显式指向项目内目录
ENV TMPDIR=/build/.esbuild-tmp TEMP=/build/.esbuild-tmp TMP=/build/.esbuild-tmp
RUN mkdir -p .esbuild-tmp && npm run build

# 阶段二：nginx 托管静态资源，并反向代理 /api 到后端
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
