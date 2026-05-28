package kdh.infra.fcm

import com.google.firebase.FirebaseApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class FcmServiceTest {

    private lateinit var fcmService: FcmService

    @BeforeEach
    fun setUp() {
        fcmService = FcmService("kdh-fcm-firebase-adminsdk-fbsvc-4e41277336.json")
    }

    @Test
    fun `init succeeds and FirebaseApp is initialized if JSON key exists`() {
        // 실제 루트 경로에 kdh-fcm-firebase-adminsdk-fbsvc-4e41277336.json 이 존재하는지 확인
        val keyFileExists = File("kdh-fcm-firebase-adminsdk-fbsvc-4e41277336.json").exists()
        
        fcmService.init()

        if (keyFileExists) {
            // 초기화가 정상 수행되었는지 확인
            assertThat(FirebaseApp.getApps()).isNotEmpty()
        }
    }

    @Test
    fun `sendNotification does not throw and skips when token is null or blank`() {
        // token이 null일 때 예외 없이 무사히 종료되는지 확인
        fcmService.sendNotification(token = null, title = "테스트 제목", body = "테스트 본문")

        // token이 blank일 때 예외 없이 무사히 종료되는지 확인
        fcmService.sendNotification(token = "  ", title = "테스트 제목", body = "테스트 본문")
    }

    @Test
    fun `sendNotification catch exception and log warning on invalid token or missing app initialization`() {
        // 유효하지 않은 가짜 토큰을 보내더라도 내부 try-catch에 의해 예외가 잡히고 무사히 실행되는지 확인
        fcmService.sendNotification(token = "invalid-mock-token-xyz", title = "테스트 제목", body = "테스트 본문")
    }
}
