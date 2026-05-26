package kdh.domain.routine.service

import kdh.domain.routine.entity.Routine
import kdh.domain.routine.repository.DailyWorkoutRepository
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.infra.fcm.FcmService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate

class RoutineNotificationSchedulerTest {

    private lateinit var routineRepository: RoutineRepository
    private lateinit var dailyWorkoutRepository: DailyWorkoutRepository
    private lateinit var fcmService: FcmService
    private lateinit var scheduler: RoutineNotificationScheduler

    @BeforeEach
    fun setUp() {
        routineRepository = Mockito.mock(RoutineRepository::class.java)
        dailyWorkoutRepository = Mockito.mock(DailyWorkoutRepository::class.java)
        fcmService = Mockito.mock(FcmService::class.java)
        scheduler = RoutineNotificationScheduler(
            routineRepository,
            dailyWorkoutRepository,
            fcmService
        )
    }

    @Test
    fun `sendWeeklyAchievementNotifications triggers FCM notification for routines completed a full week`() {
        val today = LocalDate.now()
        val user1 = User(provider = "kakao", providerId = "user-1", name = "Tester 1", fcmToken = "token-1")
        val user2 = User(provider = "kakao", providerId = "user-2", name = "Tester 2", fcmToken = "token-2")
        val user3 = User(provider = "kakao", providerId = "user-3", name = "Tester 3", fcmToken = "token-3")
        
        // 7일 전 시작한 루틴 (2주차 완료 일요일)
        val routine1 = Routine(id = 1L, user = user1, totalWeeks = 4, startDate = today.minusDays(7))
        // 14일 전 시작한 루틴 (3주차 완료 일요일)
        val routine2 = Routine(id = 2L, user = user2, totalWeeks = 4, startDate = today.minusDays(14))
        // 5일 전 시작한 루틴 (daysBetween = 5 로, completedWeek = 1 이며 daysBetween >= 5 를 만족하므로 1주차 정산 대상임)
        val routine3 = Routine(id = 3L, user = user3, totalWeeks = 4, startDate = today.minusDays(5))

        Mockito.`when`(routineRepository.findActiveRoutines(today, today.minusWeeks(25)))
            .thenReturn(listOf(routine1, routine2, routine3))

        // 이번 주 월요일 ~ 오늘 일요일
        val weekStart = today.minusDays(6)
        val weekEnd = today

        // routine1 (2주차 정산)
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "user-1", weekStart, weekEnd))
            .thenReturn(10L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "user-1", weekStart, weekEnd))
            .thenReturn(7L)

        // routine2 (3주차 정산)
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "user-2", weekStart, weekEnd))
            .thenReturn(10L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "user-2", weekStart, weekEnd))
            .thenReturn(9L)

        // routine3 (1주차 정산)
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "user-3", weekStart, weekEnd))
            .thenReturn(10L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "user-3", weekStart, weekEnd))
            .thenReturn(5L)

        scheduler.sendWeeklyAchievementNotifications()

        // routine1 (2주차 완료 알림)
        Mockito.verify(fcmService).sendNotification(
            token = "token-1",
            title = "2주차 루틴 달성률 알림",
            body = "이번 주 루틴 달성률은 70.0%입니다. 클릭해서 확인해 보세요!",
            data = mapOf(
                "type" to "ROUTINE_WEEKLY_ACHIEVEMENT",
                "routineId" to "1",
                "weekNumber" to "2",
                "achievementRate" to "70.0"
            )
        )

        // routine2 (3주차 완료 알림)
        Mockito.verify(fcmService).sendNotification(
            token = "token-2",
            title = "3주차 루틴 달성률 알림",
            body = "이번 주 루틴 달성률은 90.0%입니다. 클릭해서 확인해 보세요!",
            data = mapOf(
                "type" to "ROUTINE_WEEKLY_ACHIEVEMENT",
                "routineId" to "2",
                "weekNumber" to "3",
                "achievementRate" to "90.0"
            )
        )

        // routine3 (1주차 완료 알림)
        Mockito.verify(fcmService).sendNotification(
            token = "token-3",
            title = "1주차 루틴 달성률 알림",
            body = "이번 주 루틴 달성률은 50.0%입니다. 클릭해서 확인해 보세요!",
            data = mapOf(
                "type" to "ROUTINE_WEEKLY_ACHIEVEMENT",
                "routineId" to "3",
                "weekNumber" to "1",
                "achievementRate" to "50.0"
            )
        )
    }
}
