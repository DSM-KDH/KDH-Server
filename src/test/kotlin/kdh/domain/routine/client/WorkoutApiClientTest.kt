package kdh.domain.routine.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.ScheduleSection
import kdh.domain.routine.dto.WeeklyWorkoutApiRequest
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class WorkoutApiClientTest {

    private val objectMapper = jacksonObjectMapper()
    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `generateSingleWeekRoutine requests the whole week in one workout api call`() {
        val requestBodies = mutableListOf<WeeklyWorkoutApiRequest>()
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/workout/invoke") { exchange ->
                handleWorkoutRequest(exchange, requestBodies)
            }
            start()
        }
        val port = server!!.address.port
        val client = WorkoutApiClient("http://localhost:$port")
        client.init()

        val workouts = client.generateSingleWeekRoutine(request(activeDays = allDays()), userId = "kakao:user-1")

        assertThat(workouts).hasSize(7)
        assertThat(workouts.flatMap { it.keys }).containsExactly(
            "Day 1 focus",
            "Day 2 focus",
            "Day 3 focus",
            "Day 4 focus",
            "Day 5 focus",
            "Day 6 focus",
            "Day 7 focus"
        )
        assertThat(requestBodies).hasSize(1)
        assertThat(requestBodies.single().input.user_id).isEqualTo("kakao:user-1")
        assertThat(requestBodies.single().input.available_days_per_week).isEqualTo(7)
        assertThat(requestBodies.single().input.daily_available_time).isEqualTo(60)
        assertThat(requestBodies.single().input.total_weeks).isEqualTo(3)
        assertThat(requestBodies.single().input.active_days).containsExactly("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        assertThat(requestBodies.single().input.fitness_level).isEqualTo("BEGINNER")
        assertThat(requestBodies.single().input.goal_type).isEqualTo("HEALTH_CARE")
        assertThat(requestBodies.single().input.preferred_exercise_types).containsExactly("BODYWEIGHT", "STRENGTH")
        assertThat(requestBodies.single().input.locations).containsExactly("HOME")
        assertThat(requestBodies.single().input.equipments).containsExactly("BAND", "FOAM_ROLLER")
        assertThat(requestBodies.single().input.extra_criteria).contains("generation_week=1")
        assertThat(requestBodies.single().config.configurable.thread_id).isNotBlank()
    }

    @Test
    fun `generateMultiWeekRoutine requests every week independently`() {
        val requestBodies = mutableListOf<WeeklyWorkoutApiRequest>()
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/workout/invoke") { exchange ->
                handleWorkoutRequest(exchange, requestBodies)
            }
            start()
        }
        val port = server!!.address.port
        val client = WorkoutApiClient("http://localhost:$port")
        client.init()

        val workoutsByWeek = client.generateMultiWeekRoutine(request(activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED)), userId = "kakao:user-1")

        assertThat(workoutsByWeek).hasSize(3)
        assertThat(workoutsByWeek).allSatisfy { workouts ->
            assertThat(workouts).hasSize(2)
        }
        assertThat(requestBodies).hasSize(3)
        assertThat(requestBodies.map { it.input.extra_criteria })
            .anySatisfy { criteria -> assertThat(criteria).contains("generation_week=1") }
            .anySatisfy { criteria -> assertThat(criteria).contains("generation_week=2") }
            .anySatisfy { criteria -> assertThat(criteria).contains("generation_week=3") }
    }

    private fun handleWorkoutRequest(
        exchange: HttpExchange,
        requestBodies: MutableList<WeeklyWorkoutApiRequest>
    ) {
        val request = objectMapper.readValue<WeeklyWorkoutApiRequest>(exchange.requestBody.readBytes())
        requestBodies.add(request)
        val response = """
            {
              "output": {
                "weekly_routine": {
                  "title": "Test week",
                  "available_days_per_week": ${request.input.available_days_per_week},
                  "days": [
                    ${weeklyRoutineDaysJson(request.input.available_days_per_week)}
                  ]
                },
                "done": true
              }
            }
        """.trimIndent()
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun weeklyRoutineDaysJson(days: Int): String {
        return (1..days).joinToString(",") { dayNumber ->
            """
            {
              "day": $dayNumber,
              "title": "Day $dayNumber focus",
              "exercises": [
                {
                  "exercise_name": "exercise-$dayNumber",
                  "reps_time": "10 reps"
                }
              ]
            }
            """.trimIndent()
        }
    }

    private fun request(activeDays: List<DayOfWeek>): RoutineCreateRequest {
        return RoutineCreateRequest(
            fcmToken = "fcm-token",
            goal = GoalSection(goalType = GoalType.HEALTH_CARE),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(totalWeeks = 3, hoursPerDay = 1.0, activeDays = activeDays),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT, ExerciseType.STRENGTH),
            environment = EnvironmentSection(
                locations = listOf(LocationType.HOME),
                equipments = listOf(EquipmentType.BAND, EquipmentType.FOAM_ROLLER)
            )
        )
    }

    private fun allDays(): List<DayOfWeek> {
        return listOf(
            DayOfWeek.MON,
            DayOfWeek.TUE,
            DayOfWeek.WED,
            DayOfWeek.THU,
            DayOfWeek.FRI,
            DayOfWeek.SAT,
            DayOfWeek.SUN
        )
    }
}
