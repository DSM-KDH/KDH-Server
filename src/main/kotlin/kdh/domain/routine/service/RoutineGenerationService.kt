package kdh.domain.routine.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.client.WorkoutApiClient
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.entity.*
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.repository.UserRepository
import kdh.infra.fcm.FcmService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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
        log.info(
            "Routine generation transaction started. provider={}, providerId={}, totalWeeks={}, activeDaysPerWeek={}",
            provider,
            providerId,
            request.schedule.totalWeeks,
            request.schedule.activeDays.size
        )
        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $provider/$providerId")

        val newRoutine = Routine(user = user, totalWeeks = request.schedule.totalWeeks)

        val workoutDates = buildWorkoutDates(request)
        var dayCounter = 1
        for (week in 1..request.schedule.totalWeeks) {
            val phase = determinePhaseForWeek(week)
            log.info(
                "Generating weekly workouts. provider={}, providerId={}, week={}, phase={}, targetDays={}",
                provider,
                providerId,
                week,
                phase,
                request.schedule.activeDays.size
            )
            val weeklyWorkoutsJson = workoutApiClient.generateSingleWeekRoutine(request, phase)
            log.info(
                "Weekly workouts generated. provider={}, providerId={}, week={}, generatedDays={}",
                provider,
                providerId,
                week,
                weeklyWorkoutsJson.size
            )

            for (workoutJson in weeklyWorkoutsJson) {
                val dailyWorkout = parseAndCreateDailyWorkout(workoutJson, dayCounter, workoutDates.getOrNull(dayCounter - 1))
                dayCounter++
                log.info(
                    "Daily workout parsed. provider={}, providerId={}, day={}, workoutDate={}, sectionCount={}, exerciseCount={}",
                    provider,
                    providerId,
                    dailyWorkout.day,
                    dailyWorkout.workoutDate,
                    dailyWorkout.sections.size,
                    dailyWorkout.sections.sumOf { it.exercises.size }
                )
                newRoutine.addDailyWorkout(dailyWorkout)
            }
        }

        val savedRoutine = routineRepository.save(newRoutine)
        log.info(
            "Routine saved. routineId={}, provider={}, providerId={}, dailyWorkoutCount={}, sectionCount={}, exerciseCount={}, elapsedMs={}",
            savedRoutine.id,
            provider,
            providerId,
            savedRoutine.dailyWorkouts.size,
            savedRoutine.dailyWorkouts.sumOf { it.sections.size },
            savedRoutine.dailyWorkouts.sumOf { dailyWorkout -> dailyWorkout.sections.sumOf { it.exercises.size } },
            System.currentTimeMillis() - startedAt
        )

        fcmService.sendNotification(
            "루틴 생성 완료!",
            "${request.schedule.totalWeeks}주 동안의 맞춤형 운동 루틴이 준비되었습니다."
        )
        log.info("Routine completion notification requested. routineId={}, provider={}, providerId={}", savedRoutine.id, provider, providerId)
    }

    private fun buildWorkoutDates(request: RoutineCreateRequest): List<LocalDate> {
        val activeDays = request.schedule.activeDays.map { it.toJavaDayOfWeek() }.toSet()
        val targetCount = request.schedule.totalWeeks * request.schedule.activeDays.size
        val workoutDates = mutableListOf<LocalDate>()
        var date = LocalDate.now()

        while (workoutDates.size < targetCount) {
            if (date.dayOfWeek in activeDays) {
                workoutDates.add(date)
            }
            date = date.plusDays(1)
        }

        return workoutDates
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

    private fun parseAndCreateDailyWorkout(workoutJson: Map<String, Any>, day: Int, workoutDate: LocalDate?): DailyWorkout {
        val dailyWorkout = DailyWorkout(day = day, workoutDate = workoutDate)

        workoutJson.forEach { (sectionName, exercisesRaw) ->
            if (exercisesRaw is List<*>) {
                val workoutSection = WorkoutSection(name = sectionName)
                
                val exercisesList = objectMapper.convertValue(exercisesRaw, object : TypeReference<List<Map<String, Any>>>() {})

                exercisesList.forEach { exerciseMap ->
                    // API는 exercise 한 개와 문자열 alternatives 배열을 한 묶음으로 내려준다.
                    val mainExercise = createExerciseDetail(exerciseMap, isAlternative = false)
                    workoutSection.addExercise(mainExercise)

                    createAlternativeExercises(exerciseMap["alternatives"])
                        .forEach(workoutSection::addExercise)
                }
                dailyWorkout.addSection(workoutSection)
            } else {
                log.warn(
                    "Skipping unexpected workout section payload. day={}, sectionName={}, payloadType={}",
                    day,
                    sectionName,
                    exercisesRaw.javaClass.name
                )
            }
        }
        return dailyWorkout
    }

    private fun createExerciseDetail(exerciseMap: Map<String, Any>, isAlternative: Boolean): ExerciseDetail {
        return ExerciseDetail(
            exerciseName = exerciseMap["exercise"] as? String ?: "이름 없음",
            sets = exerciseMap["sets"]?.toString(),
            reps = exerciseMap["reps"]?.toString(),
            rest = exerciseMap["rest"]?.toString(),
            description = exerciseMap["description"] as? String,
            isAlternative = isAlternative
        )
    }

    private fun createAlternativeExercises(alternativesRaw: Any?): List<ExerciseDetail> {
        return when (alternativesRaw) {
            is List<*> -> alternativesRaw.mapNotNull { alternative ->
                when (alternative) {
                    is String -> ExerciseDetail(
                        exerciseName = alternative,
                        sets = null,
                        reps = null,
                        rest = null,
                        description = null,
                        isAlternative = true
                    )
                    is Map<*, *> -> {
                        val alternativeMap = alternative.entries
                            .filter { it.key != null && it.value != null }
                            .associate { it.key.toString() to it.value as Any }
                        createExerciseDetail(alternativeMap, isAlternative = true)
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    private fun determinePhaseForWeek(week: Int): Int {
        return when {
            week == 1 -> 1
            week <= 3 -> 2
            else -> 3
        }
    }
}
