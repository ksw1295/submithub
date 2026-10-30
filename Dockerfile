# 1단계: Maven 으로 빌드 (테스트는 CI/로컬에서 이미 돌렸으므로 건너뛴다)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# 2단계: 실행 이미지
FROM eclipse-temurin:17-jre
# 이 이미지가 어느 커밋에서 빌드됐는지 기록한다 (docs/DEPLOY.md 7-1 의 배포 버전 확인)
ARG GIT_COMMIT=unknown
LABEL git.commit=$GIT_COMMIT
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
RUN useradd --system --create-home submithub && mkdir -p /app/uploads && chown -R submithub /app
COPY --from=build /build/target/submithub-*.jar /app/app.jar
USER submithub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
