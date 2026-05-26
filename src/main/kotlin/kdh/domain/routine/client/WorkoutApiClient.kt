package kdh.domain.routine.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import kdh.domain.routine.dto.Configurable
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.WeeklyRoutine
import kdh.domain.routine.dto.WeeklyRoutineDay
import kdh.domain.routine.dto.WeeklyWorkoutApiInput
import kdh.domain.routine.dto.WeeklyWorkoutApiRequest
import kdh.domain.routine.dto.WeeklyWorkoutApiResponse
import kdh.domain.routine.dto.WorkoutApiConfig
import kdh.domain.routine.exception.WorkoutApiEmptyResponseException
import kdh.domain.routine.exception.WorkoutApiFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.UUID

@Component
class WorkoutApiClient(
    @Value("\${external.api.workout.url}") private val workoutApiUrl: String
) {
    private companion object {
        const val MAX_WORKOUT_API_RETRY_COUNT = 3
        const val RETRY_DELAY_MILLIS = 2_000L
        const val MAX_PARALLEL_WEEK_GENERATION = 4
    }

    private lateinit var webClient: WebClient
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        webClient = WebClient.builder().baseUrl(workoutApiUrl).build()
        log.info("Workout API client initialized. baseUrl={}", workoutApiUrl)
    }

    fun generateSingleWeekRoutine(request: RoutineCreateRequest, userId: String, feedback: String? = null): List<Map<String, Any>> {
        return generateWeekRoutine(request, userId, weekNumber = 1, feedback = feedback)
    }

    fun generateMultiWeekRoutine(request: RoutineCreateRequest, userId: String, feedback: String? = null): List<List<Map<String, Any>>> {
        val startedAt = System.currentTimeMillis()
        val totalWeeks = request.schedule.totalWeeks
        val poolSize = totalWeeks.coerceAtMost(MAX_PARALLEL_WEEK_GENERATION)
        val executor = Executors.newFixedThreadPool(poolSize)

        return try {
            val futures = (1..totalWeeks).map { week ->
                CompletableFuture.supplyAsync(
                    { generateWeekRoutine(request, userId, week, feedback) },
                    executor
                )
            }
            val weeklyRoutines = futures.map { future ->
                try {
                    future.join()
                } catch (e: CompletionException) {
                    throw e.cause ?: e
                }
            }
            log.info(
                "Workout API multi-week generation finished. userId={}, totalWeeks={}, poolSize={}, generatedWeeks={}, elapsedMs={}",
                userId,
                totalWeeks,
                poolSize,
                weeklyRoutines.size,
                System.currentTimeMillis() - startedAt
            )
            weeklyRoutines
        } finally {
            executor.shutdown()
        }
    }

    private fun generateWeekRoutine(
        request: RoutineCreateRequest,
        userId: String,
        weekNumber: Int,
        feedback: String?
    ): List<Map<String, Any>> {
        val threadId = UUID.randomUUID().toString()
        val targetWorkoutCount = request.schedule.activeDays.size
        val startedAt = System.currentTimeMillis()
        val state = createWeeklyState(request, userId, threadId, weekNumber, feedback)

        log.info(
            "Workout API weekly generation started. threadId={}, userId={}, weekNumber={}, targetWorkoutCount={}, activeDays={}, hoursPerDay={}, totalWeeks={}, goalType={}, fitnessLevel={}, requestBytes={}",
            threadId,
            userId,
            weekNumber,
            targetWorkoutCount,
            request.schedule.activeDays,
            request.schedule.hoursPerDay,
            request.schedule.totalWeeks,
            request.goal.goalType,
            request.fitnessLevel,
            objectMapper.writeValueAsBytes(state).size
        )

        val (responseJson, weeklyRoutine) = requestWeeklyWorkoutApiWithRetry(state, threadId, userId)
        val weeklyWorkouts = convertWeeklyRoutineToLegacyWorkouts(weeklyRoutine, targetWorkoutCount)

        log.info(
            "Workout API weekly generation finished. threadId={}, userId={}, weekNumber={}, generatedCount={}, targetWorkoutCount={}, responseBytes={}, elapsedMs={}",
            threadId,
            userId,
            weekNumber,
            weeklyWorkouts.size,
            targetWorkoutCount,
            responseJson.toByteArray().size,
            System.currentTimeMillis() - startedAt
        )
        return weeklyWorkouts
    }

    private fun requestWeeklyWorkoutApiWithRetry(
        state: WeeklyWorkoutApiRequest,
        threadId: String,
        userId: String
    ): Pair<String, WeeklyRoutine> {
        var lastError: Exception? = null

        for (retryAttempt in 1..MAX_WORKOUT_API_RETRY_COUNT) {
            val retryStartedAt = System.currentTimeMillis()
            try {
                log.info(
                    "Workout API retry attempt started. threadId={}, userId={}, retryAttempt={}/{}, apiPath={}, requestBytes={}",
                    threadId,
                    userId,
                    retryAttempt,
                    MAX_WORKOUT_API_RETRY_COUNT,
                    "/workout/invoke",
                    objectMapper.writeValueAsBytes(state).size
                )

                val responseJson = postWeeklyToWorkoutApi(state)
                val responseState = objectMapper.readValue<WeeklyWorkoutApiResponse>(responseJson).output
                log.info(
                    "Workout API call succeeded. threadId={}, userId={}, retryAttempt={}/{}, responseBytes={}, done={}, generatedDays={}, elapsedMs={}",
                    threadId,
                    userId,
                    retryAttempt,
                    MAX_WORKOUT_API_RETRY_COUNT,
                    responseJson.toByteArray().size,
                    responseState.done,
                    responseState.weekly_routine.days.size,
                    System.currentTimeMillis() - retryStartedAt
                )
                return responseJson to responseState.weekly_routine
            } catch (e: Exception) {
                lastError = e
                log.warn(
                    "Workout API call failed. threadId={}, userId={}, retryAttempt={}/{}, exceptionClass={}, message={}, elapsedMs={}",
                    threadId,
                    userId,
                    retryAttempt,
                    MAX_WORKOUT_API_RETRY_COUNT,
                    e.javaClass.name,
                    e.message,
                    System.currentTimeMillis() - retryStartedAt,
                    e
                )

                if (retryAttempt < MAX_WORKOUT_API_RETRY_COUNT) {
                    val delayMs = RETRY_DELAY_MILLIS * retryAttempt
                    Thread.sleep(delayMs)
                }
            }
        }

        throw WorkoutApiFailedException(MAX_WORKOUT_API_RETRY_COUNT, lastError)
    }

    private fun postWeeklyToWorkoutApi(state: WeeklyWorkoutApiRequest): String {
        return webClient.post()
            .uri("/workout/invoke")
            .bodyValue(state)
            .retrieve()
            .bodyToMono(String::class.java)
            .block() ?: throw WorkoutApiEmptyResponseException()
    }

    private fun createWeeklyState(
        request: RoutineCreateRequest,
        userId: String,
        threadId: String,
        weekNumber: Int,
        feedback: String?
    ): WeeklyWorkoutApiRequest {
        val input = WeeklyWorkoutApiInput(
            user_id = userId,
            available_days_per_week = request.schedule.activeDays.size,
            daily_available_time = (request.schedule.hoursPerDay * 60).toInt(),
            total_weeks = request.schedule.totalWeeks,
            active_days = request.schedule.activeDays.map { it.name },
            fitness_level = request.fitnessLevel.name,
            goal_type = request.goal.goalType.name,
            target_weight = request.goal.targetWeight,
            target_body_parts = request.goal.targetBodyParts?.map { it.name } ?: emptyList(),
            preferred_exercise_types = request.preferredExerciseTypes.map { it.name },
            locations = request.environment.locations.map { it.name },
            equipments = request.environment.equipments.map { it.name },
            extra_criteria = buildExtraCriteria(request, weekNumber, feedback),
            equipment_limitations = buildEquipmentLimitations(request),
            goals = buildGoals(request),
            client_info = "User ID: $userId; generation_week=$weekNumber/${request.schedule.totalWeeks}"
        )
        val config = WorkoutApiConfig(configurable = Configurable(thread_id = threadId))
        return WeeklyWorkoutApiRequest(input = input, config = config)
    }

    private fun buildGoals(request: RoutineCreateRequest): String {
        return buildString {
            append("goal_type=${request.goal.goalType.name}")
            request.goal.targetWeight?.let { append("; target_weight=$it kg") }
            val targetBodyParts = request.goal.targetBodyParts.orEmpty()
            if (targetBodyParts.isNotEmpty()) {
                append("; target_body_parts=${targetBodyParts.joinToString(",") { it.name }}")
            }
        }
    }

    private fun buildExtraCriteria(request: RoutineCreateRequest, weekNumber: Int, feedback: String?): String {
        val criteriaList = mutableListOf(
            "fitness_level=${request.fitnessLevel.name}",
            "total_weeks=${request.schedule.totalWeeks}",
            "generation_week=$weekNumber",
            "active_days=${request.schedule.activeDays.joinToString(",") { it.name }}",
            "hours_per_day=${request.schedule.hoursPerDay}",
            "preferred_exercise_types=${request.preferredExerciseTypes.joinToString(",") { it.name }}",
            "locations=${request.environment.locations.joinToString(",") { it.name }}",
            "equipments=${request.environment.equipments.joinToString(",") { it.name }}"
        )
        if (!feedback.isNullOrBlank()) {
            criteriaList.add("user_feedback=$feedback")
        }
        return criteriaList.joinToString("; ")
    }

    private fun buildEquipmentLimitations(request: RoutineCreateRequest): String {
        val locations = request.environment.locations.joinToString(", ") { it.name.lowercase() }
        val equipments = request.environment.equipments.joinToString(", ") { it.name.lowercase() }
        return "locations: $locations; available equipment: $equipments"
    }

    private fun convertWeeklyRoutineToLegacyWorkouts(
        weeklyRoutine: WeeklyRoutine,
        targetWorkoutCount: Int
    ): List<Map<String, Any>> {
        val workouts = weeklyRoutine.days
            .take(targetWorkoutCount)
            .mapIndexed { index, day ->
                convertWeeklyRoutineDayToLegacyWorkout(day, index + 1)
            }
            .filter { it.isNotEmpty() }

        if (workouts.isEmpty()) {
            throw WorkoutApiEmptyResponseException()
        }

        return workouts
    }

    private fun convertWeeklyRoutineDayToLegacyWorkout(
        day: WeeklyRoutineDay,
        fallbackDayNumber: Int
    ): Map<String, Any> {
        if (day.exercises.isEmpty()) {
            return emptyMap()
        }

        val sectionName = day.title.ifBlank { "Day ${day.day.takeIf { it > 0 } ?: fallbackDayNumber}" }
        return mapOf(sectionName to day.exercises)
    }
}
