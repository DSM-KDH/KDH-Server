package kdh.domain.routine.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.routine.dto.ScheduleSection
import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType
import kdh.domain.routine.exception.ExerciseCompletionDateInvalidException
import kdh.domain.routine.exception.ExerciseNotFoundException
import kdh.domain.routine.exception.ExerciseWorkoutDateNotFoundException
import kdh.domain.routine.exception.FutureRoutineExistsException
import kdh.domain.routine.exception.ProfileRequiredException
import kdh.domain.routine.repository.DailyWorkoutRepository
import kdh.domain.routine.repository.ExerciseDetailRepository
import kdh.domain.user.entity.User
import kdh.domain.user.exception.UserNotFoundException
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import kdh.captureValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.LocalDate

class RoutineServiceTest {

    private lateinit var rabbitTemplate: RabbitTemplate
    private lateinit var userRepository: UserRepository
    private lateinit var profileRepository: UserProfileHistoryRepository
    private lateinit var dailyWorkoutRepository: DailyWorkoutRepository
    private lateinit var exerciseRepository: ExerciseDetailRepository
    private lateinit var service: RoutineService

    @BeforeEach
    fun setUp() {
        rabbitTemplate = Mockito.mock(RabbitTemplate::class.java)
        userRepository = Mockito.mock(UserRepository::class.java)
        profileRepository = Mockito.mock(UserProfileHistoryRepository::class.java)
        dailyWorkoutRepository = Mockito.mock(DailyWorkoutRepository::class.java)
        exerciseRepository = Mockito.mock(ExerciseDetailRepository::class.java)
        service = RoutineService(
            rabbitTemplate,
            userRepository,
            profileRepository,
            dailyWorkoutRepository,
            exerciseRepository
        )
    }

    @Test
    fun `createRoutine publishes queue message when validations pass`() {
        val request = request()
        val today = LocalDate.now()
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(profileRepository.existsByUserProviderAndUserProviderId("kakao", "user-1")).thenReturn(true)
        Mockito.`when`(dailyWorkoutRepository.findFirstFutureWorkoutDate("kakao", "user-1", today)).thenReturn(null)

        service.createRoutine(request, "kakao", "user-1")

        val messageCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(rabbitTemplate).convertAndSend(
            Mockito.eq("routine.exchange"),
            Mockito.eq("routine.create.key"),
            captureValue(messageCaptor)
        )
        val message = jacksonObjectMapper().readValue<RoutineCreationMessage>(messageCaptor.value)
        assertThat(message.provider).isEqualTo("kakao")
        assertThat(message.providerId).isEqualTo("user-1")
        assertThat(message.request).isEqualTo(request)
    }

    @Test
    fun `createRoutine rejects missing user profile and existing future routine`() {
        val request = request()
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "missing")).thenReturn(null)

        assertThatThrownBy { service.createRoutine(request, "kakao", "missing") }
            .isInstanceOf(UserNotFoundException::class.java)

        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(profileRepository.existsByUserProviderAndUserProviderId("kakao", "user-1")).thenReturn(false)
        assertThatThrownBy { service.createRoutine(request, "kakao", "user-1") }
            .isInstanceOf(ProfileRequiredException::class.java)

        Mockito.`when`(profileRepository.existsByUserProviderAndUserProviderId("kakao", "user-1")).thenReturn(true)
        Mockito.`when`(dailyWorkoutRepository.findFirstFutureWorkoutDate("kakao", "user-1", LocalDate.now()))
            .thenReturn(LocalDate.now().plusDays(1))
        assertThatThrownBy { service.createRoutine(request, "kakao", "user-1") }
            .isInstanceOf(FutureRoutineExistsException::class.java)

        Mockito.verifyNoInteractions(rabbitTemplate)
    }

    @Test
    fun `getMyRoutineByDate flattens workouts by section and exercise`() {
        val date = LocalDate.of(2026, 5, 11)
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = date)
        val warmUp = WorkoutSection(name = "1. Warm up")
        warmUp.addExercise(ExerciseDetail(id = 1L, exerciseName = "Walk", repsTime = "5 min"))
        val strength = WorkoutSection(name = "Strength")
        strength.addExercise(ExerciseDetail(id = 2L, exerciseName = "Squat", completed = true))
        dailyWorkout.addSection(warmUp)
        dailyWorkout.addSection(strength)
        Mockito.`when`(
            dailyWorkoutRepository.findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate("kakao", "user-1", date)
        ).thenReturn(listOf(dailyWorkout))

        val response = service.getMyRoutineByDate(date, "kakao", "user-1")

        assertThat(response.date).isEqualTo(date)
        assertThat(response.workouts).extracting<String> { it.exerciseName }
            .containsExactly("Walk", "Squat")
        assertThat(response.workouts).extracting<Boolean> { it.completed }
            .containsExactly(false, true)
    }

    @Test
    fun `getMyRoutineByDate flattens a large routine day without dropping exercises`() {
        val date = LocalDate.of(2026, 5, 14)
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = date)
        var exerciseId = 1L
        repeat(10) { sectionIndex ->
            val section = WorkoutSection(name = "Section ${sectionIndex + 1}")
            repeat(10) { exerciseIndex ->
                section.addExercise(
                    ExerciseDetail(
                        id = exerciseId++,
                        exerciseName = "Exercise ${sectionIndex + 1}-${exerciseIndex + 1}",
                        repsTime = "${exerciseIndex + 1}0 reps",
                        completed = exerciseIndex % 2 == 0
                    )
                )
            }
            dailyWorkout.addSection(section)
        }
        Mockito.`when`(
            dailyWorkoutRepository.findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate("kakao", "user-1", date)
        ).thenReturn(listOf(dailyWorkout))

        val response = service.getMyRoutineByDate(date, "kakao", "user-1")

        assertThat(response.workouts).hasSize(100)
        assertThat(response.workouts.first().sectionName).isEqualTo("Section 1")
        assertThat(response.workouts.first().exerciseName).isEqualTo("Exercise 1-1")
        assertThat(response.workouts.last().sectionName).isEqualTo("Section 10")
        assertThat(response.workouts.last().exerciseName).isEqualTo("Exercise 10-10")
        assertThat(response.workouts.count { it.completed }).isEqualTo(50)
    }

    @Test
    fun `getMyRoutineDates queries from one month ago and returns repository dates`() {
        val expectedDates = listOf(LocalDate.now(), LocalDate.now().plusDays(2))
        val fromDate = LocalDate.now().minusMonths(1)
        Mockito.`when`(dailyWorkoutRepository.findDistinctWorkoutDatesFrom("kakao", "user-1", fromDate))
            .thenReturn(expectedDates)

        val dates = service.getMyRoutineDates("kakao", "user-1")

        Mockito.verify(dailyWorkoutRepository).findDistinctWorkoutDatesFrom("kakao", "user-1", fromDate)
        assertThat(dates).isEqualTo(expectedDates)
    }

    @Test
    fun `getLastWeekAchievementRate returns zero for no exercises and percentage otherwise`() {
        val startDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .minusWeeks(1)
        val endDate = startDate.plusDays(6)
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "empty", startDate, endDate))
            .thenReturn(0L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "empty", startDate, endDate))
            .thenReturn(0L)
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "user-1", startDate, endDate))
            .thenReturn(8L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "user-1", startDate, endDate))
            .thenReturn(6L)

        val emptyResponse = service.getLastWeekAchievementRate("kakao", "empty")
        val response = service.getLastWeekAchievementRate("kakao", "user-1")

        assertThat(emptyResponse.achievementRate).isEqualTo(0.0)
        assertThat(response.totalExerciseCount).isEqualTo(8L)
        assertThat(response.completedExerciseCount).isEqualTo(6L)
        assertThat(response.achievementRate).isEqualTo(75.0)
        assertThat(response.endDate).isEqualTo(response.startDate.plusDays(6))
    }

    @Test
    fun `updateExerciseCompletion updates only today's owned exercise`() {
        val exercise = ownedExercise(workoutDate = LocalDate.now(), completed = false)
        Mockito.`when`(
            exerciseRepository.findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                1L,
                "kakao",
                "user-1"
            )
        ).thenReturn(exercise)

        val response = service.updateExerciseCompletion(1L, true, "kakao", "user-1")

        assertThat(exercise.completed).isTrue()
        assertThat(response.exerciseId).isEqualTo(1L)
        assertThat(response.completed).isTrue()
    }

    @Test
    fun `updateExerciseCompletion rejects missing exercise missing date and non today workout`() {
        Mockito.`when`(
            exerciseRepository.findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                99L,
                "kakao",
                "user-1"
            )
        ).thenReturn(null)
        assertThatThrownBy { service.updateExerciseCompletion(99L, true, "kakao", "user-1") }
            .isInstanceOf(ExerciseNotFoundException::class.java)

        Mockito.`when`(
            exerciseRepository.findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                2L,
                "kakao",
                "user-1"
            )
        ).thenReturn(ownedExercise(workoutDate = null))
        assertThatThrownBy { service.updateExerciseCompletion(2L, true, "kakao", "user-1") }
            .isInstanceOf(ExerciseWorkoutDateNotFoundException::class.java)

        Mockito.`when`(
            exerciseRepository.findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                3L,
                "kakao",
                "user-1"
            )
        ).thenReturn(ownedExercise(workoutDate = LocalDate.now().minusDays(1)))
        assertThatThrownBy { service.updateExerciseCompletion(3L, true, "kakao", "user-1") }
            .isInstanceOf(ExerciseCompletionDateInvalidException::class.java)
    }

    private fun ownedExercise(workoutDate: LocalDate?, completed: Boolean = false): ExerciseDetail {
        val routine = Routine(user = user(), totalWeeks = 1)
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = workoutDate)
        val section = WorkoutSection(name = "Strength")
        val exercise = ExerciseDetail(id = 1L, exerciseName = "Squat", completed = completed)
        routine.addDailyWorkout(dailyWorkout)
        dailyWorkout.addSection(section)
        section.addExercise(exercise)
        return exercise
    }

    private fun request(): RoutineCreateRequest {
        return RoutineCreateRequest(
            fcmToken = "fcm-token",
            goal = GoalSection(goalType = GoalType.HEALTH_CARE),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(
                totalWeeks = 4,
                hoursPerDay = 1,
                activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED)
            ),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(
                locations = listOf(LocationType.HOME),
                equipments = listOf(EquipmentType.BAND)
            )
        )
    }

    private fun user(): User {
        return User(provider = "kakao", providerId = "user-1", name = "Tester")
    }
}
