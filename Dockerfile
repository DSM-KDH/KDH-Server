# 1단계: 빌드 환경 (Gradle 사용 가정)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# 그래들 빌드에 필요한 파일들 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# 실행 권한 부여 및 JAR 파일 빌드 (테스트는 생략해서 속도 향상)
RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar -x test --console=plain --scan --no-daemon -Dscan.acceptOpenSourceLicense=true

# 2단계: 실행 환경 (가벼운 JRE 사용)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 빌드 단계에서 생성된 JAR 파일만 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 컨테이너가 시작될 때 실행할 명령어
# Jenkinsfile에서 넘겨준 환경변수(DB_URL 등)가 여기서 적용됨
ENTRYPOINT ["java", "-jar", "app.jar"]