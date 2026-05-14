package kdh.domain.routine.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.ExternalWorkoutApiRequest
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.ScheduleSection
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
    fun `generateSingleWeekRoutine splits seven active days into daily workout api calls`() {
        val requestBodies = mutableListOf<ExternalWorkoutApiRequest>()
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/workout/invoke") { exchange ->
                handleWorkoutRequest(exchange, requestBodies)
            }
            start()
        }
        val port = server!!.address.port
        val client = WorkoutApiClient("http://localhost:$port")
        client.init()

        val workouts = client.generateSingleWeekRoutine(request(activeDays = allDays()), phase = 1)

        assertThat(workouts).hasSize(7)
        assertThat(workouts.flatMap { it.keys }).containsExactly(
            "Day 1",
            "Day 2",
            "Day 3",
            "Day 4",
            "Day 5",
            "Day 6",
            "Day 7"
        )
        assertThat(requestBodies).hasSize(7)
        assertThat(requestBodies.map { it.input.workouts_in_week }).containsOnly(1)
        assertThat(requestBodies.map { it.input.day }).containsExactly(0, 1, 2, 3, 4, 5, 6)
        assertThat(requestBodies.map { it.input.created_workouts.size }).containsExactly(0, 1, 2, 3, 4, 5, 6)
        assertThat(requestBodies.map { it.input.thread_id }.distinct()).hasSize(1)
    }

    private fun handleWorkoutRequest(
        exchange: HttpExchange,
        requestBodies: MutableList<ExternalWorkoutApiRequest>
    ) {
        val request = objectMapper.readValue<ExternalWorkoutApiRequest>(exchange.requestBody.readBytes())
        requestBodies.add(request)
        val dayNumber = requestBodies.size
        val response = """
            {
              "output": {
                "day": $dayNumber,
                "phase": ${request.input.phase},
                "workouts_in_week": 1,
                "workout_length": "${request.input.workout_length}",
                "extra_criteria": "test",
                "current_workout": {
                  "Day $dayNumber": [
                    {
                      "exercise_name": "exercise-$dayNumber",
                      "reps_time": "10 reps"
                    }
                  ]
                },
                "created_workouts": [],
                "client_info": "${request.input.client_info}",
                "user_feedback": "",
                "done": true,
                "thread_id": "${request.input.thread_id}"
              }
            }
        """.trimIndent()
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun request(activeDays: List<DayOfWeek>): RoutineCreateRequest {
        return RoutineCreateRequest(
            fcmToken = "fcm-token",
            goal = GoalSection(goalType = GoalType.HEALTH_CARE),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(totalWeeks = 3, hoursPerDay = 1, activeDays = activeDays),
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
