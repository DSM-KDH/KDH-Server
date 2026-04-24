package kdh.domain.routine.service

import kdh.domain.routine.client.WorkoutApiClient
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.infra.fcm.FcmService
import org.springframework.stereotype.Service

@Service
class RoutineGenerationService(
    private val workoutApiClient: WorkoutApiClient,
    private val fcmService: FcmService
    // private val routineRepository: RoutineRepository // TODO: 생성된 루틴을 저장할 Repository
) {

    fun generateMultiWeekRoutine(request: RoutineCreateRequest) {
        val allWorkouts = mutableListOf<Map<String, Any>>()

        for (week in 1..request.schedule.totalWeeks) {
            val phase = determinePhaseForWeek(week)
            val weeklyWorkouts = workoutApiClient.generateSingleWeekRoutine(request, phase)
            allWorkouts.addAll(weeklyWorkouts)
        }

        // TODO: 생성된 allWorkouts를 DB에 저장하는 로직
        // 예: val routine = Routine(userId = ..., workouts = allWorkouts)
        // routineRepository.save(routine)

        fcmService.sendNotification(
            "루틴 생성 완료!",
            "${request.schedule.totalWeeks}주 동안의 맞춤형 운동 루틴이 준비되었습니다."
        )
    }

    private fun determinePhaseForWeek(week: Int): Int {
        return when {
            week == 1 -> 1 // 1주차: 안정화
            week <= 3 -> 2 // 2-3주차: 근지구력
            else -> 3      // 4주차 이상: 근육 발달
        }
    }
}