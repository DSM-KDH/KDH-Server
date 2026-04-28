package kdh.domain.routine.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.client.WorkoutApiClient
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.repository.UserRepository
import kdh.infra.fcm.FcmService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class RoutineGenerationService(
    private val workoutApiClient: WorkoutApiClient,
    private val fcmService: FcmService,
    private val routineRepository: RoutineRepository,
    private val userRepository: UserRepository
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun generateMultiWeekRoutine(request: RoutineCreateRequest, provider: String, providerId: String) {
        val startedAt = System.currentTimeMillis()
        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $provider/$providerId")

        val routineStartDate = LocalDate.now()
        val newRoutine = Routine(user = user, totalWeeks = request.schedule.totalWeeks, startDate = routineStartDate)

        var dayCounter = 1
        for (week in 1..request.schedule.totalWeeks) {
            val phase = determinePhaseForWeek(week)
            val weeklyWorkoutsJson = workoutApiClient.generateSingleWeekRoutine(request, phase)
            val workoutDates = request.schedule.activeDays.map { activeDay ->
                routineStartDate
                    .plusWeeks((week - 1).toLong())
                    .with(TemporalAdjusters.nextOrSame(activeDay.toJavaDayOfWeek()))
            }

            for ((index, workoutJson) in weeklyWorkoutsJson.withIndex()) {
                val workoutDate = workoutDates.getOrNull(index) ?: routineStartDate.plusDays((dayCounter - 1).toLong())
                newRoutine.addDailyWorkout(parseAndCreateDailyWorkout(workoutJson, dayCounter++, workoutDate))
            }
        }

        val savedRoutine = routineRepository.save(newRoutine)
        log.info(
            "Routine saved. routineId={}, provider={}, providerId={}, dailyWorkoutCount={}, elapsedMs={}",
            savedRoutine.id,
            provider,
            providerId,
            savedRoutine.dailyWorkouts.size,
            System.currentTimeMillis() - startedAt
        )

        fcmService.sendNotification(
            "루틴 생성 완료!",
            "${request.schedule.totalWeeks}주 동안의 맞춤형 운동 루틴이 준비되었습니다."
        )
    }

    private fun parseAndCreateDailyWorkout(
        workoutJson: Map<String, Any>,
        day: Int,
        workoutDate: LocalDate
    ): DailyWorkout {
        val dailyWorkout = DailyWorkout(day = day, workoutDate = workoutDate)

        workoutJson.forEach { (sectionName, exercisesRaw) ->
            if (exercisesRaw !is List<*>) {
                log.warn("Skipping unexpected workout section payload. day={}, sectionName={}", day, sectionName)
                return@forEach
            }

            val workoutSection = WorkoutSection(name = sectionName)
            val exercisesList = objectMapper.convertValue(
                exercisesRaw,
                object : TypeReference<List<Map<String, Any>>>() {}
            )

            exercisesList
                .map(::createExerciseDetail)
                .forEach(workoutSection::addExercise)

            dailyWorkout.addSection(workoutSection)
        }

        return dailyWorkout
    }

    private fun createExerciseDetail(exerciseMap: Map<String, Any>): ExerciseDetail {
        return ExerciseDetail(
            exerciseName = exerciseMap["exercise_name"] as? String
                ?: exerciseMap["exerciseName"] as? String
                ?: exerciseMap["exercise"] as? String
                ?: "이름 없음",
            repsTime = exerciseMap["reps_time"]?.toString()
                ?: exerciseMap["repsTime"]?.toString()
                ?: exerciseMap["reps"]?.toString()
        )
    }

    private fun determinePhaseForWeek(week: Int): Int {
        return when {
            week == 1 -> 1
            week <= 3 -> 2
            else -> 3
        }
    }

    private fun kdh.domain.routine.enum.DayOfWeek.toJavaDayOfWeek(): java.time.DayOfWeek {
        return when (this) {
            kdh.domain.routine.enum.DayOfWeek.MON -> java.time.DayOfWeek.MONDAY
            kdh.domain.routine.enum.DayOfWeek.TUE -> java.time.DayOfWeek.TUESDAY
            kdh.domain.routine.enum.DayOfWeek.WED -> java.time.DayOfWeek.WEDNESDAY
            kdh.domain.routine.enum.DayOfWeek.THU -> java.time.DayOfWeek.THURSDAY
            kdh.domain.routine.enum.DayOfWeek.FRI -> java.time.DayOfWeek.FRIDAY
            kdh.domain.routine.enum.DayOfWeek.SAT -> java.time.DayOfWeek.SATURDAY
            kdh.domain.routine.enum.DayOfWeek.SUN -> java.time.DayOfWeek.SUNDAY
        }
    }
}
