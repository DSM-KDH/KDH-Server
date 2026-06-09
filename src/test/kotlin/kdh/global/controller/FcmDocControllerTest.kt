package kdh.global.controller

import kdh.infra.fcm.FcmService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate

class FcmDocControllerTest {

    private lateinit var fcmService: FcmService
    private lateinit var controller: FcmDocController

    @BeforeEach
    fun setUp() {
        fcmService = Mockito.mock(FcmService::class.java)
        controller = FcmDocController(fcmService)
    }

    @Test
    fun `testRoutineCompleted sends fcm notification and returns 1-week dummy workouts`() {
        val fcmToken = "test-token"
        val totalWeeks = 4
        val startDate = LocalDate.of(2026, 6, 8)
        val request = FcmTestRoutineCompletedRequest(
            fcmToken = fcmToken,
            totalWeeks = totalWeeks,
            startDate = startDate
        )

        val responseEntity = controller.testRoutineCompleted(request)
        val response = responseEntity.body!!

        assertThat(responseEntity.statusCode.value()).isEqualTo(200)
        assertThat(response.fcmStatus).isEqualTo("SUCCESS")
        assertThat(response.fcmMessage).contains("FCM 푸시 전송 성공")
        assertThat(response.dummyRoutine).hasSize(3)

        // Verify dates of dummy workouts
        assertThat(response.dummyRoutine[0].date).isEqualTo(startDate)
        assertThat(response.dummyRoutine[1].date).isEqualTo(startDate.plusDays(2))
        assertThat(response.dummyRoutine[2].date).isEqualTo(startDate.plusDays(4))

        // Verify first day exercises
        val day1Workouts = response.dummyRoutine[0].workouts
        assertThat(day1Workouts).hasSize(3)
        assertThat(day1Workouts[0].exerciseName).isEqualTo("제자리 걷기 March in place")
        assertThat(day1Workouts[1].exerciseName).isEqualTo("밴드 스쿼트 Band squat")
        assertThat(day1Workouts[2].exerciseName).isEqualTo("스트레칭 Hamstring stretch")

        // Verify FCM service mock was called
        val expectedData = mapOf(
            "type" to "ROUTINE_GENERATION",
            "status" to "COMPLETED",
            "phase" to "COMPLETED",
            "createdCount" to "12",
            "totalCount" to "12",
            "progressPercent" to "100",
            "estimatedFirstWeekMinutes" to "5",
            "estimatedFirstWeekRemainingMinutes" to "0",
            "estimatedTotalMinutes" to "8",
            "estimatedRemainingMinutes" to "0",
            "completed" to "true"
        )
        Mockito.verify(fcmService).sendNotification(
            token = fcmToken,
            title = "루틴 생성 완료!",
            body = "4주 동안의 맞춤 운동 루틴이 준비됐어요.",
            data = expectedData
        )
    }
}
