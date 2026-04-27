# 1. 빌드 스테이지
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# 설정 파일 및 래퍼 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle* settings.gradle* ./
RUN chmod +x gradlew

# [핵심] 전체 소스 코드를 먼저 복사!
COPY src src

# 빌드 실행 (clean 후 bootJar만 생성)
RUN ./gradlew clean bootJar -x test

# 2. 실행 스테이지
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일 가져오기
# -plain.jar가 같이 생성될 수 있어서 명확하게 패턴 지정
COPY --from=builder /build/build/libs/*[0-9].jar app.jar

RUN apk add --no-cache tzdata
ENV TZ=Asia/Seoul

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "/app/app.jar"]
