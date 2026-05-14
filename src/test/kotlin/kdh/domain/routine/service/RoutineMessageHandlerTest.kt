package kdh.domain.routine.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.routine.dto.ScheduleSection
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType
import kdh.domain.routine.exception.RoutineGenerationFailedException
import kdh.domain.user.exception.UserNotFoundException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class RoutineMessageHandlerTest {

    private val objectMapper = jacksonObjectMapper()
    private lateinit var routineGenerationService: RoutineGenerationService
    private lateinit var handler: RoutineMessageHandler

    @BeforeEach
    fun setUp() {
        routineGenerationService = Mockito.mock(RoutineGenerationService::class.java)
        handler = RoutineMessageHandler(routineGenerationService)
    }

    @Test
    fun `handleMessage skips stale message when user no longer exists`() {
        val payload = message()
        Mockito.doThrow(UserNotFoundException("codex", "stale-user"))
            .`when`(routineGenerationService)
            .generateMultiWeekRoutine(payload.request, payload.provider, payload.providerId)

        handler.handleMessage(toJson(payload))

        Mockito.verify(routineGenerationService)
            .generateMultiWeekRoutine(payload.request, payload.provider, payload.providerId)
    }

    @Test
    fun `handleMessage wraps unexpected generation failure`() {
        val payload = message()
        Mockito.doThrow(RuntimeException("api timeout"))
            .`when`(routineGenerationService)
            .generateMultiWeekRoutine(payload.request, payload.provider, payload.providerId)

        assertThatThrownBy { handler.handleMessage(toJson(payload)) }
            .isInstanceOf(RoutineGenerationFailedException::class.java)
    }

    private fun toJson(message: RoutineCreationMessage): String {
        return objectMapper.writeValueAsString(message)
    }

    private fun message(): RoutineCreationMessage {
        return RoutineCreationMessage(
            provider = "codex",
            providerId = "stale-user",
            request = RoutineCreateRequest(
                fcmToken = "fcm-token",
                goal = GoalSection(goalType = GoalType.HEALTH_CARE),
                fitnessLevel = FitnessLevel.BEGINNER,
                schedule = ScheduleSection(
                    totalWeeks = 3,
                    hoursPerDay = 1,
                    activeDays = listOf(DayOfWeek.MON, DayOfWeek.TUE)
                ),
                preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT, ExerciseType.STRENGTH),
                environment = EnvironmentSection(
                    locations = listOf(LocationType.HOME),
                    equipments = listOf(EquipmentType.BAND, EquipmentType.FOAM_ROLLER)
                )
            )
        )
    }
}
