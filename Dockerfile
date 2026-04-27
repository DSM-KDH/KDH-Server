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

# [수정] 빌드된 jar 파일을 확실하게 가져오기
# 보통 bootJar는 build/libs 아래에 하나만 생성됨
COPY --from=builder /build/build/libs/*.jar app.jar

# 파일이 제대로 복사됐는지 권한 체크 (혹시 모르니)
RUN chmod +x app.jar

RUN apk add --no-cache tzdata
ENV TZ=Asia/Seoul

EXPOSE 8080

# [수정] 절대 경로 대신 상대 경로로 실행
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "app.jar"]
