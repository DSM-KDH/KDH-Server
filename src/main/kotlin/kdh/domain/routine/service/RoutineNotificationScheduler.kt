package kdh.domain.routine.service

import kdh.domain.routine.repository.DailyWorkoutRepository
import kdh.domain.routine.repository.RoutineRepository
import kdh.infra.fcm.FcmService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class RoutineNotificationScheduler(
    private val routineRepository: RoutineRepository,
    private val dailyWorkoutRepository: DailyWorkoutRepository,
    private val fcmService: FcmService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 21 * * SUN")
    @Transactional(readOnly = true)
    fun sendWeeklyAchievementNotifications() {
        val today = LocalDate.now()
        // 루틴 기간의 최대치(24주)를 감안하여 최근 25주 동안 시작된 루틴만 조회
        val cutoffDate = today.minusWeeks(25)
        val activeRoutines = routineRepository.findActiveRoutines(
            today = today,
            cutoffDate = cutoffDate
        )

        log.info("Starting weekly routine achievement notification check. activeRoutinesCount={}", activeRoutines.size)

        activeRoutines.forEach { routine ->
            val daysBetween = ChronoUnit.DAYS.between(routine.startDate, today)
            val completedWeek = (daysBetween / 7).toInt() + 1
            
            // 일요일에 생성된 당일 루틴 등을 제외하기 위해 최소 5일 이상 경과 기준
            if (daysBetween >= 5 && completedWeek <= routine.totalWeeks) {
                val user = routine.user
                val fcmToken = user.fcmToken
                
                if (!fcmToken.isNullOrBlank()) {
                    // 이번 주 월요일부터 일요일(오늘)까지의 기간으로 달성률 고정
                    val weekStartDate = today.minusDays(6)
                    val weekEndDate = today

                    val totalCount = dailyWorkoutRepository.countExercisesBetweenDates(
                        provider = user.provider,
                        providerId = user.providerId,
                        startDate = weekStartDate,
                        endDate = weekEndDate
                    )
                    val completedCount = dailyWorkoutRepository.countCompletedExercisesBetweenDates(
                        provider = user.provider,
                        providerId = user.providerId,
                        startDate = weekStartDate,
                        endDate = weekEndDate
                    )
                    val achievementRate = if (totalCount == 0L) {
                        0.0
                    } else {
                        completedCount.toDouble() / totalCount.toDouble() * 100
                    }

                    fcmService.sendNotification(
                        token = fcmToken,
                        title = "${completedWeek}주차 루틴 달성률 알림",
                        body = "이번 주 루틴 달성률은 ${String.format("%.1f", achievementRate)}%입니다. 클릭해서 확인해 보세요!",
                        data = mapOf(
                            "type" to "ROUTINE_WEEKLY_ACHIEVEMENT",
                            "routineId" to routine.id.toString(),
                            "weekNumber" to completedWeek.toString(),
                            "achievementRate" to achievementRate.toString()
                        )
                    )
                    log.info(
                        "Weekly achievement notification sent. provider={}, providerId={}, routineId={}, completedWeek={}, achievementRate={}%",
                        user.provider,
                        user.providerId,
                        routine.id,
                        completedWeek,
                        achievementRate
                    )
                } else {
                    log.debug(
                        "Skipped weekly routine achievement notification due to missing FCM token. provider={}, providerId={}, routineId={}",
                        user.provider,
                        user.providerId,
                        routine.id
                    )
                }
            }
        }
    }
}
