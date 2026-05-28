package kdh.infra.fcm

import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:fcm-integration-test;NON_KEYWORDS=DAY;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class FcmServiceIntegrationTest @Autowired constructor(
    private val fcmService: FcmService
) {

    @Test
    fun `actual google fcm v1 handshake test using local json credentials`() {
        // 실제 구글 FCM 서버와 연동하여 가짜 토큰을 통해 네트워크 및 OAuth2 인증 handshake가 정상 완료되는지 확인하는 블랙박스 테스트
        // 유효하지 않은 가짜 토큰이므로 발송 자체는 구글 측으로부터 오류 응답(Unregistered 등)을 수신하게 된다.
        // 핵심은 어떠한 미처리 예외도 밖으로 던져지지 않고, try-catch 내에서 로깅 처리된 후 무사히 리턴되는지 여부이다.
        
        val fakeToken = "bk3RNwY3D0w:CI2g_w36gc43g839dhg38g38dhg39dhg38g38g38g38g38g38g38"
        
        assertThatNoException().isThrownBy {
            fcmService.sendNotification(
                token = fakeToken,
                title = "블랙박스 통합 테스트 푸시 알림",
                body = "실제 구글 FCM 서버와 v1 연동 블랙박스 테스트 메시지입니다.",
                data = mapOf("testKey" to "testValue")
            )
        }
    }
}
