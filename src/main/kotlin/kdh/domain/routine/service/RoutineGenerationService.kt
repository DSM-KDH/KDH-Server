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

    fun generateMultiWeekRoutine(request: RoutineCreateRequest, provider: String, providerId: String) {
        val startedAt = System.currentTimeMillis()
        val targetWorkoutCount = request.schedule.totalWeeks * request.schedule.activeDays.size

        log.info(
            "Routine generation started. provider={}, providerId={}, totalWeeks={}, activeDays={}, hoursPerDay={}, goalType={}, targetWeight={}, targetBodyParts={}, fitnessLevel={}, preferredExerciseTypes={}, locations={}, equipments={}, targetWorkoutCount={}",
            provider,
            providerId,
            request.schedule.totalWeeks,
            request.schedule.activeDays,
            request.schedule.hoursPerDay,
            request.goal.goalType,
            request.goal.targetWeight,
            request.goal.targetBodyParts,
            request.fitnessLevel,
            request.preferredExerciseTypes,
            request.environment.locations,
            request.environment.equipments,
            targetWorkoutCount
        )

        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $provider/$providerId")

        log.info(
            "Routine generation owner found. provider={}, providerId={}, userName={}",
            provider,
            providerId,
            user.name
        )

        val routineStartDate = LocalDate.now()
        var routine = routineRepository.saveAndFlush(
            Routine(user = user, totalWeeks = request.schedule.totalWeeks, startDate = routineStartDate)
        )
        log.info(
            "Routine container saved before generation. routineId={}, provider={}, providerId={}, startDate={}, totalWeeks={}",
            routine.id,
            provider,
            providerId,
            routineStartDate,
            request.schedule.totalWeeks
        )

        var dayCounter = 1
        val workoutDatesByGenerationOrder = generateWorkoutDates(
            startDate = routineStartDate,
            activeDays = request.schedule.activeDays,
            count = targetWorkoutCount
        )

        log.info(
            "Routine workout dates planned. routineId={}, startDate={}, activeDays={}, targetWorkoutCount={}, plannedDates={}",
            routine.id,
            routineStartDate,
            request.schedule.activeDays,
            targetWorkoutCount,
            workoutDatesByGenerationOrder
        )

        for (week in 1..request.schedule.totalWeeks) {
            val weekStartedAt = System.currentTimeMillis()
            val phase = determinePhaseForWeek(week)
            val workoutDates = workoutDatesByGenerationOrder
                .drop((week - 1) * request.schedule.activeDays.size)
                .take(request.schedule.activeDays.size)

            log.info(
                "Routine week generation started. routineId={}, week={}, phase={}, activeDays={}, plannedDates={}",
                routine.id,
                week,
                phase,
                request.schedule.activeDays,
                workoutDates
            )

            val weeklyWorkoutsJson = workoutApiClient.generateSingleWeekRoutine(request, phase)
            log.info(
                "Routine week generation API completed. routineId={}, week={}, phase={}, generatedDays={}, expectedDays={}, elapsedMs={}",
                routine.id,
                week,
                phase,
                weeklyWorkoutsJson.size,
                request.schedule.activeDays.size,
                System.currentTimeMillis() - weekStartedAt
            )

            if (weeklyWorkoutsJson.size < request.schedule.activeDays.size) {
                log.warn(
                    "Routine week generation returned fewer workouts than expected. routineId={}, week={}, generatedDays={}, expectedDays={}",
                    routine.id,
                    week,
                    weeklyWorkoutsJson.size,
                    request.schedule.activeDays.size
                )
            }

            for ((index, workoutJson) in weeklyWorkoutsJson.withIndex()) {
                val workoutDate = workoutDates.getOrNull(index) ?: routineStartDate.plusDays((dayCounter - 1).toLong())
                val parseStartedAt = System.currentTimeMillis()

                log.info(
                    "Daily workout parse started. routineId={}, week={}, day={}, indexInWeek={}, workoutDate={}, sectionKeys={}",
                    routine.id,
                    week,
                    dayCounter,
                    index,
                    workoutDate,
                    workoutJson.keys
                )

                val dailyWorkout = parseAndCreateDailyWorkout(workoutJson, dayCounter, workoutDate)
                val sectionCount = dailyWorkout.sections.size
                val exerciseCount = dailyWorkout.sections.sumOf { it.exercises.size }

                log.info(
                    "Daily workout parsed. routineId={}, week={}, day={}, workoutDate={}, sectionCount={}, exerciseCount={}, elapsedMs={}",
                    routine.id,
                    week,
                    dayCounter,
                    workoutDate,
                    sectionCount,
                    exerciseCount,
                    System.currentTimeMillis() - parseStartedAt
                )

                routine.addDailyWorkout(dailyWorkout)
                routine = routineRepository.saveAndFlush(routine)
                log.info(
                    "Daily workout saved. routineId={}, provider={}, providerId={}, week={}, day={}, workoutDate={}, savedDailyWorkoutCount={}, savedSectionCount={}, savedExerciseCount={}",
                    routine.id,
                    provider,
                    providerId,
                    week,
                    dayCounter,
                    workoutDate,
                    routine.dailyWorkouts.size,
                    routine.dailyWorkouts.sumOf { it.sections.size },
                    routine.dailyWorkouts.sumOf { daily -> daily.sections.sumOf { it.exercises.size } }
                )

                dayCounter += 1
            }
        }

        val savedRoutine = routineRepository.saveAndFlush(routine)
        log.info(
            "Routine generation completed. routineId={}, provider={}, providerId={}, dailyWorkoutCount={}, sectionCount={}, exerciseCount={}, elapsedMs={}",
            savedRoutine.id,
            provider,
            providerId,
            savedRoutine.dailyWorkouts.size,
            savedRoutine.dailyWorkouts.sumOf { it.sections.size },
            savedRoutine.dailyWorkouts.sumOf { daily -> daily.sections.sumOf { it.exercises.size } },
            System.currentTimeMillis() - startedAt
        )

        fcmService.sendNotification(
            "루틴 생성 완료!",
            "${request.schedule.totalWeeks}주 동안의 맞춤형 운동 루틴이 준비되었습니다."
        )
        log.info(
            "Routine generation notification sent. routineId={}, provider={}, providerId={}",
            savedRoutine.id,
            provider,
            providerId
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
                log.warn(
                    "Skipping unexpected workout section payload. day={}, workoutDate={}, sectionName={}, payloadType={}",
                    day,
                    workoutDate,
                    sectionName,
                    exercisesRaw.javaClass.name
                )
                return@forEach
            }

            val workoutSection = WorkoutSection(name = sectionName)
            val exercisesList = objectMapper.convertValue(
                exercisesRaw,
                object : TypeReference<List<Map<String, Any>>>() {}
            )

            exercisesList
                .mapIndexed { index, exerciseMap ->
                    val exercise = createExerciseDetail(exerciseMap)
                    log.debug(
                        "Exercise parsed. day={}, workoutDate={}, sectionName={}, exerciseIndex={}, exerciseName={}, repsTime={}, sourceKeys={}",
                        day,
                        workoutDate,
                        sectionName,
                        index,
                        exercise.exerciseName,
                        exercise.repsTime,
                        exerciseMap.keys
                    )
                    exercise
                }
                .forEach(workoutSection::addExercise)

            dailyWorkout.addSection(workoutSection)
            log.info(
                "Workout section parsed. day={}, workoutDate={}, sectionName={}, exerciseCount={}",
                day,
                workoutDate,
                sectionName,
                workoutSection.exercises.size
            )
        }

        if (dailyWorkout.sections.isEmpty()) {
            log.warn(
                "Daily workout parsed with no sections. day={}, workoutDate={}, sourceKeys={}",
                day,
                workoutDate,
                workoutJson.keys
            )
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

    private fun generateWorkoutDates(
        startDate: LocalDate,
        activeDays: List<kdh.domain.routine.enum.DayOfWeek>,
        count: Int
    ): List<LocalDate> {
        val activeJavaDays = activeDays.map { it.toJavaDayOfWeek() }.toSet()
        val workoutDates = mutableListOf<LocalDate>()
        var cursor = startDate

        while (workoutDates.size < count) {
            if (cursor.dayOfWeek in activeJavaDays) {
                workoutDates.add(cursor)
            }
            cursor = cursor.plusDays(1)
        }

        return workoutDates
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
