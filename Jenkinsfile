pipeline {
    agent any

    environment {
        // [수정 포인트 1] 프로젝트 이름 (도커 컨테이너 및 이미지 이름)
        APP_NAME = "ootdrop"

        // 젠킨스에 등록한 깃허브 Credential ID
        GIT_CREDENTIAL_ID = "github-token-auth"
    }

    stages {
        stage('1. 환경 변수 설정') {
            steps {
                script {
                    if (env.BRANCH_NAME == 'main') {
                        env.PORT = "8000"          // 운영 포트
                        env.PHASE = "prod"         // 운영 단계
                        env.DB_NAME = "ootdrop" // 운영 DB 스키마 이름
                        env.DOMAIN = "ootdrop.ajswl.website"
                    } else if (env.BRANCH_NAME == 'develop') {
                        env.PORT = "8001"          // 스테이징 포트
                        env.PHASE = "stg"          // 스테이징 단계
                        env.DB_NAME = "ootdrop_stg"  // 스테이징 DB 스키마 이름
                        env.DOMAIN = "ootdrop-stg.ajswl.website"
                    } else {
                        error "지원하지 않는 브랜치입니다: ${env.BRANCH_NAME}"
                    }

                }
                // 'my-env-file'이라는 ID를 가진 파일을 envFile이라는 변수로 가져옵니다.
                withCredentials([file(credentialsId: 'my-env-file', variable: 'envFile')]) {
                    // 가져온 파일을 현재 워크스페이스에 .env라는 이름으로 복사합니다.
                    script {
                        sh "cp ${envFile} .env"
                        // 확인용 (보안을 위해 실제 운영 환경에선 cat은 빼는게 좋아요)
                        sh "ls -la .env"
                    }
                }
            }
        }

        stage('2. 도커 이미지 빌드') {
            steps {
                echo "Building Docker Image: ${env.APP_NAME}:${env.PHASE}"
                // 현재 폴더(.)에 있는 Dockerfile을 읽어서 빌드합니다.
                sh "docker build -t ${env.APP_NAME}:${env.PHASE} . "
            }
        }

        stage('3. 도커 컨테이너 배포') {
            steps {
                script {
                    echo "Deploying to ${env.PHASE} (Port: ${env.PORT}, DB: ${env.DB_NAME})"

                    // 기존 컨테이너 중지 및 삭제
                    sh "docker stop ${env.APP_NAME}-${env.PHASE} || true"
                    sh "docker rm ${env.APP_NAME}-${env.PHASE} || true"

                    // 도커 실행 옵션 설명:
                    // --memory="1g": 램 사용량 1GB 제한 (t3.large 8GB 중 1/8)
                    // --memory-swap="1g": 스왑 메모리도 1GB로 제한해서 디스크 I/O 병목 방지
                    // -e DB_SCHEMA: 애플리케이션 내부에서 사용할 DB 이름 전달
                    // --log-opt: 로그 파일이 서버 용량을 다 채우지 않도록 30MB까지만 보관
                    sh """
                        docker run -d \
                        --name ${env.APP_NAME}-${env.PHASE} \
                        -p ${env.PORT}:8080 \
                        --add-host=host.docker.internal:host-gateway \
                        --env-file .env \
                        -e JAVA_OPTS="-Xmx384M -Xms256M" \
                        -e SPRING_PROFILES_ACTIVE=${env.PHASE} \
                        -e DB_URL=jdbc:mysql://host.docker.internal:3306/${env.DB_NAME} \
                        -e DOMAIN=${env.DOMAIN}
                        --memory="512m" \
                        --memory-swap="512m" \
                        --log-opt max-size=10m --log-opt max-file=3 \
                        ${env.APP_NAME}:${env.PHASE}
                    """
                }
            }
        }
        stage('4. 도커 컨테이너 상태 확인') {
            steps {
                script {
                    echo "⏳ 서버가 시작될 때까지 대기 중... (최대 3분)"
                        try {
                            timeout(time: 1, unit: 'MINUTES') {
                                waitUntil {
                                // 8080이 아니라 호스트 배포 포트(${env.PORT})로 확인
                                // -s: 정적(silent), -f: HTTP 에러 시 실패 처리, -o: 출력 버림
                                    def response = sh(
                                        script: "curl -s -f http://localhost:${env.PORT}/swagger-ui/index.html || curl -s -f http://localhost:${env.PORT}/",
                                        returnStatus: true
                                    )
                                return (response == 0)
                            }
                        }
                        echo "✅ 서버가 정상적으로 구동되었습니다!"
                    } catch (err) {
                        echo "❌ 서버 구동 실패! 도커 로그를 확인합니다."
                        sh "docker logs ${env.APP_NAME}-${env.PHASE}"
                        // 컨테이너가 떴는데 응답만 안 오는 걸 수도 있으므로, 실패 시 컨테이너를 중지할지 선택 가능
                        // sh "docker stop ${env.CONTAINER_NAME}"
                        error "Application Health Check Failed: ${err.message}"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ 배포 완료! 주소: http://서버IP:${env.PORT} (DB 스키마: ${env.DB_NAME})"
        }
        failure {
            echo "❌ 배포 실패! 로그를 확인하세요."
        }
    }
}