package kdh.domain.routine.service

import kdh.domain.routine.client.WorkoutApiClient
import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.ScheduleSection
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.domain.user.exception.UserNotFoundException
import kdh.domain.user.repository.UserRepository
import kdh.infra.fcm.FcmService
import kdh.anyValue
import kdh.captureValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDate

class RoutineGenerationServiceTest {

    private lateinit var workoutApiClient: WorkoutApiClient
    private lateinit var fcmService: FcmService
    private lateinit var routineRepository: RoutineRepository
    private lateinit var userRepository: UserRepository
    private lateinit var service: RoutineGenerationService

    @BeforeEach
    fun setUp() {
        workoutApiClient = Mockito.mock(WorkoutApiClient::class.java)
        fcmService = Mockito.mock(FcmService::class.java)
        routineRepository = Mockito.mock(RoutineRepository::class.java)
        userRepository = Mockito.mock(UserRepository::class.java)
        service = RoutineGenerationService(workoutApiClient, fcmService, routineRepository, userRepository)
    }

    @Test
    fun `generateMultiWeekRoutine saves parsed workouts with expected phases and notification`() {
        val request = request(totalWeeks = 4, activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED))
        val weeklyWorkouts = listOf(
            workout("Warm up", "walk", "5 min"),
            workout("Strength", "squat", "10 reps")
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateSingleWeekRoutine(request, 1)).thenReturn(weeklyWorkouts)
        Mockito.`when`(workoutApiClient.generateSingleWeekRoutine(request, 2)).thenReturn(weeklyWorkouts)
        Mockito.`when`(workoutApiClient.generateSingleWeekRoutine(request, 3)).thenReturn(weeklyWorkouts)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        Mockito.verify(workoutApiClient).generateSingleWeekRoutine(request, 1)
        Mockito.verify(workoutApiClient, Mockito.times(2)).generateSingleWeekRoutine(request, 2)
        Mockito.verify(workoutApiClient).generateSingleWeekRoutine(request, 3)

        val routineCaptor = ArgumentCaptor.forClass(Routine::class.java)
        Mockito.verify(routineRepository, Mockito.atLeastOnce()).saveAndFlush(captureValue(routineCaptor))
        val savedRoutine = routineCaptor.allValues.last()
        assertThat(savedRoutine.dailyWorkouts).hasSize(8)
        assertThat(savedRoutine.dailyWorkouts.map { it.day }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(savedRoutine.dailyWorkouts.flatMap { it.sections }.map { it.name })
            .contains("Warm up", "Strength")
        assertThat(savedRoutine.dailyWorkouts.flatMap { daily -> daily.sections.flatMap { it.exercises } })
            .extracting<String> { it.exerciseName }
            .contains("walk", "squat")
        assertThat(savedRoutine.dailyWorkouts.mapNotNull { it.workoutDate })
            .allSatisfy { date ->
                assertThat(date).isAfterOrEqualTo(LocalDate.now())
                assertThat(date.dayOfWeek).isIn(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY)
            }
        Mockito.verify(fcmService).sendNotification(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `generateMultiWeekRoutine saves three weeks of daily workouts`() {
        val activeDays = listOf(
            DayOfWeek.MON,
            DayOfWeek.TUE,
            DayOfWeek.WED,
            DayOfWeek.THU,
            DayOfWeek.FRI,
            DayOfWeek.SAT,
            DayOfWeek.SUN
        )
        val request = request(totalWeeks = 3, activeDays = activeDays)
        val weeklyWorkouts = activeDays.mapIndexed { index, day ->
            workout("Day ${index + 1} $day", "exercise-$day", "${index + 1}0 reps")
        }
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateSingleWeekRoutine(request, 1)).thenReturn(weeklyWorkouts)
        Mockito.`when`(workoutApiClient.generateSingleWeekRoutine(request, 2)).thenReturn(weeklyWorkouts)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        Mockito.verify(workoutApiClient).generateSingleWeekRoutine(request, 1)
        Mockito.verify(workoutApiClient, Mockito.times(2)).generateSingleWeekRoutine(request, 2)
        Mockito.verify(workoutApiClient, Mockito.never()).generateSingleWeekRoutine(request, 3)

        val routineCaptor = ArgumentCaptor.forClass(Routine::class.java)
        Mockito.verify(routineRepository, Mockito.atLeastOnce()).saveAndFlush(captureValue(routineCaptor))
        val savedRoutine = routineCaptor.allValues.last()
        assertThat(savedRoutine.totalWeeks).isEqualTo(3)
        assertThat(savedRoutine.dailyWorkouts).hasSize(21)
        assertThat(savedRoutine.dailyWorkouts.map { it.day }).containsExactlyElementsOf((1..21).toList())
        assertThat(savedRoutine.dailyWorkouts.mapNotNull { it.workoutDate }).hasSize(21)
        assertThat(savedRoutine.dailyWorkouts.flatMap { it.sections }).hasSize(21)
        assertThat(savedRoutine.dailyWorkouts.flatMap { daily -> daily.sections.flatMap { it.exercises } })
            .hasSize(21)
        Mockito.verify(fcmService).sendNotification(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `generateMultiWeekRoutine throws when user is missing and does not call api`() {
        val request = request()
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "missing")).thenReturn(null)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "missing") }
            .isInstanceOf(UserNotFoundException::class.java)

        Mockito.verifyNoInteractions(workoutApiClient, routineRepository, fcmService)
    }

    @Test
    fun `generateMultiWeekRoutine does not save empty routine when workout api fails`() {
        val request = request(totalWeeks = 3, activeDays = listOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED))
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateSingleWeekRoutine(request, 1))
            .thenThrow(RuntimeException("workout api timeout"))

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("workout api timeout")

        Mockito.verify(routineRepository, Mockito.never()).saveAndFlush(anyValue())
        Mockito.verifyNoInteractions(fcmService)
    }

    private fun workout(sectionName: String, exerciseName: String, repsTime: String): Map<String, Any> {
        return mapOf(
            sectionName to listOf(
                mapOf(
                    "exercise_name" to exerciseName,
                    "reps_time" to repsTime
                )
            )
        )
    }

    private fun request(
        totalWeeks: Int = 1,
        activeDays: List<DayOfWeek> = listOf(DayOfWeek.MON)
    ): RoutineCreateRequest {
        return RoutineCreateRequest(
            fcmToken = "fcm-token",
            goal = GoalSection(goalType = GoalType.MUSCLE_GAIN),
            fitnessLevel = FitnessLevel.INTERMEDIATE,
            schedule = ScheduleSection(totalWeeks = totalWeeks, hoursPerDay = 1, activeDays = activeDays),
            preferredExerciseTypes = listOf(ExerciseType.STRENGTH),
            environment = EnvironmentSection(
                locations = listOf(LocationType.GYM),
                equipments = listOf(EquipmentType.DUMBBELL, EquipmentType.BENCH)
            )
        )
    }

    private fun user(): User {
        return User(provider = "kakao", providerId = "user-1", name = "Tester")
    }
}
