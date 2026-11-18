# ========== PandaGo 后端镜像（多阶段构建） ==========
# 阶段一：用 Maven + JDK17 编译打包，构建产物是一个可执行 fat jar
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷贝 pom.xml 下载依赖，利用 Docker 层缓存：pom 不变时跳过重复下载
COPY pom.xml .
RUN mvn -B -f pom.xml dependency:go-offline

# 再拷贝源码打包（-DskipTests：测试在 CI/本地单独执行，镜像构建不重复跑）
COPY src ./src
RUN mvn -B -f pom.xml clean package -DskipTests

# 阶段二：运行时镜像，只用 JRE，体积更小、攻击面更小
FROM eclipse-temurin:17-jre
WORKDIR /app

# 安装 curl 供 compose 健康检查使用（Actuator health 端点）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 用户运行（安全基线）
RUN useradd -ms /bin/bash pandago && chown -R pandago:pandago /app
USER pandago

# 从构建阶段拷贝 fat jar（版本用通配符，避免升级版本号时改 Dockerfile）
COPY --from=build /build/target/pandago-backend-*.jar app.jar

EXPOSE 8080

# 时区与 JVM 参数可通过环境变量覆盖；JVM 内存默认限制避免容器 OOM
ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
