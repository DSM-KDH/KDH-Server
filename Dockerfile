# 1. 빌드 스테이지
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# 그래들 캐시 활용을 위해 설정 파일 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
RUN ./gradlew build -x test --stacktrace || return 0

# 전체 소스 복사 후 빌드
COPY src src
RUN ./gradlew clean build -x test

# 2. 실행 스테이지
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일만 가져옴
COPY --from=builder /build/build/libs/*.jar app.jar

# 타임존 설정 (한국 시간 기준)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Seoul

# 8080 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "/app/app.jar"]
