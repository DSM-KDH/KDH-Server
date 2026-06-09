package kdh.domain.routine.controller

import kdh.domain.routine.dto.ExerciseCompletionResponse
import kdh.domain.routine.dto.RoutineAchievementRateResponse
import kdh.domain.routine.dto.RoutineDateResponse
import kdh.domain.routine.dto.RoutineDeleteResponse
import kdh.domain.routine.service.RoutineService
import kdh.global.oauth.CustomOAuth2User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate

class RoutineControllerTest {

    private lateinit var routineService: RoutineService
    private lateinit var controller: RoutineController

    @BeforeEach
    fun setUp() {
        routineService = Mockito.mock(RoutineService::class.java)
        controller = RoutineController(routineService)
    }

    @Test
    fun `createRoutine delegates authenticated owner to service`() {
        val request = RoutineTestFixtures.request()

        val response = controller.createRoutine(request, principal())

        assertThat(response.statusCode.value()).isEqualTo(200)
        Mockito.verify(routineService).createRoutine(request, "kakao", "user-1")
    }

    @Test
    fun `getMyRoutineByDate returns service response`() {
        val date = LocalDate.of(2026, 5, 11)
        val expected = RoutineDateResponse(date = date, workouts = emptyList())
        Mockito.`when`(routineService.getMyRoutineByDate(date, "kakao", "user-1")).thenReturn(expected)

        val response = controller.getMyRoutineByDate(date, principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `getMyRoutineDates returns date list for authenticated user`() {
        val expected = listOf(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13))
        Mockito.`when`(routineService.getMyRoutineDates("kakao", "user-1")).thenReturn(expected)

        val response = controller.getMyRoutineDates(principal())

        assertThat(response.body).isEqualTo(expected)
    }

    @Test
    fun `deleteFutureRoutines delegates authenticated owner to service`() {
        val expected = RoutineDeleteResponse(
            deletedRoutineCount = 1,
            deletedDailyWorkoutCount = 28,
            deletedWorkoutSectionCount = 28,
            deletedExerciseCount = 224
        )
        Mockito.`when`(routineService.deleteFutureRoutines("kakao", "user-1")).thenReturn(expected)

        val response = controller.deleteFutureRoutines(principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `getLastWeekAchievementRate returns service response`() {
        val expected = RoutineAchievementRateResponse(
            startDate = LocalDate.of(2026, 5, 4),
            endDate = LocalDate.of(2026, 5, 10),
            totalExerciseCount = 8,
            completedExerciseCount = 6,
            achievementRate = 75.0
        )
        Mockito.`when`(routineService.getLastWeekAchievementRate("kakao", "user-1")).thenReturn(expected)

        val response = controller.getLastWeekAchievementRate(principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `updateExerciseCompletion delegates path and query parameters`() {
        val expected = ExerciseCompletionResponse(exerciseId = 7L, completed = true)
        Mockito.`when`(routineService.updateExerciseCompletion(7L, true, "kakao", "user-1")).thenReturn(expected)

        val response = controller.updateExerciseCompletion(7L, true, principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `validateRoutineCondition delegates authenticated owner to service`() {
        val request = RoutineTestFixtures.request()

        val response = controller.validateRoutineCondition(request, principal())

        assertThat(response.statusCode.value()).isEqualTo(200)
        Mockito.verify(routineService).validateCreationConditionOnly(request, "kakao", "user-1")
    }

    @Test
    fun `deleteExercise delegates exerciseId and owner to service`() {
        val response = controller.deleteExercise(5L, principal())

        assertThat(response.statusCode.value()).isEqualTo(200)
        Mockito.verify(routineService).deleteExercise(5L, "kakao", "user-1")
    }


    private fun principal(): CustomOAuth2User {
        return CustomOAuth2User(userName = "Tester", provider = "kakao", providerId = "user-1")
    }
}
