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
import kdh.domain.routine.exception.InvalidDietConditionException
import kdh.domain.routine.exception.RegenerationLimitExceededException
import kdh.domain.routine.repository.DailyWorkoutRepository
import kdh.domain.routine.repository.ExerciseDetailRepository
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.domain.user.exception.UserNotFoundException
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowableOfType
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
    private lateinit var routineRepository: RoutineRepository
    private lateinit var service: RoutineService

    @BeforeEach
    fun setUp() {
        rabbitTemplate = Mockito.mock(RabbitTemplate::class.java)
        userRepository = Mockito.mock(UserRepository::class.java)
        profileRepository = Mockito.mock(UserProfileHistoryRepository::class.java)
        dailyWorkoutRepository = Mockito.mock(DailyWorkoutRepository::class.java)
        exerciseRepository = Mockito.mock(ExerciseDetailRepository::class.java)
        routineRepository = Mockito.mock(RoutineRepository::class.java)
        service = RoutineService(
            rabbitTemplate,
            userRepository,
            profileRepository,
            dailyWorkoutRepository,
            exerciseRepository,
            routineRepository
        )
    }

    @Test
    fun `createRoutine publishes queue message when validations pass`() {
        val request = request()
        val today = LocalDate.now()
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1"))
            .thenReturn(UserProfileHistory(user = user(), heightCm = 175.0, weightKg = 70.0, gender = Gender.MALE))
        Mockito.`when`(dailyWorkoutRepository.findFirstFutureWorkoutDate("kakao", "user-1", today)).thenReturn(null)

        service.createRoutine(request, "kakao", "user-1")

        val messageCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(rabbitTemplate).convertAndSend(
            Mockito.eq("routine.exchange"),
            Mockito.eq("routine.create.key"),
            messageCaptor.capture()
        )
        val message = jacksonObjectMapper().readValue<RoutineCreationMessage>(messageCaptor.value)
        assertThat(message.provider).isEqualTo("kakao")
        assertThat(message.providerId).isEqualTo("user-1")
        assertThat(message.request.schedule.totalWeeks).isEqualTo(request.schedule.totalWeeks)
    }

    @Test
    fun `createRoutine rejects missing user profile and existing future routine`() {
        val request = request()
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "missing")).thenReturn(null)

        assertThatThrownBy { service.createRoutine(request, "kakao", "missing") }
            .isInstanceOf(UserNotFoundException::class.java)

        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1"))
            .thenReturn(null)
        assertThatThrownBy { service.createRoutine(request, "kakao", "user-1") }
            .isInstanceOf(ProfileRequiredException::class.java)

        Mockito.`when`(profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1"))
            .thenReturn(UserProfileHistory(user = user(), heightCm = 175.0, weightKg = 70.0, gender = Gender.MALE))
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
    fun `deleteFutureRoutines deletes routines containing future workout dates`() {
        val routine = Routine(id = 10L, user = user(), totalWeeks = 4)
        val todayWorkout = DailyWorkout(day = 1, workoutDate = LocalDate.now())
        val futureWorkout = DailyWorkout(day = 2, workoutDate = LocalDate.now().plusDays(1))
        val section = WorkoutSection(name = "Strength")
        section.addExercise(ExerciseDetail(id = 1L, exerciseName = "Squat"))
        futureWorkout.addSection(section)
        routine.addDailyWorkout(todayWorkout)
        routine.addDailyWorkout(futureWorkout)
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(
            routineRepository.findRoutinesWithFutureWorkouts("kakao", "user-1", LocalDate.now())
        ).thenReturn(listOf(routine))

        val response = service.deleteFutureRoutines("kakao", "user-1")

        assertThat(response.deletedRoutineCount).isEqualTo(1)
        assertThat(response.deletedDailyWorkoutCount).isEqualTo(2)
        assertThat(response.deletedWorkoutSectionCount).isEqualTo(1)
        assertThat(response.deletedExerciseCount).isEqualTo(1)
        Mockito.verify(routineRepository).deleteAll(listOf(routine))
    }

    @Test
    fun `deleteFutureRoutines returns zero counts when nothing is scheduled`() {
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(
            routineRepository.findRoutinesWithFutureWorkouts("kakao", "user-1", LocalDate.now())
        ).thenReturn(emptyList())

        val response = service.deleteFutureRoutines("kakao", "user-1")

        assertThat(response.deletedRoutineCount).isZero()
        assertThat(response.deletedDailyWorkoutCount).isZero()
        assertThat(response.deletedWorkoutSectionCount).isZero()
        assertThat(response.deletedExerciseCount).isZero()
        Mockito.verify(routineRepository, Mockito.never()).deleteAll(Mockito.anyList())
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
                hoursPerDay = 1.0,
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

    @Test
    fun `createRoutine blocks unsafe BMI or diet goals`() {
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        
        // 1. 심각한 저체중 BMI < 16.0 차단
        val skinnyProfile = UserProfileHistory(user = user(), heightCm = 175.0, weightKg = 40.0, gender = Gender.FEMALE)
        Mockito.`when`(profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1"))
            .thenReturn(skinnyProfile)
        val ex1 = catchThrowableOfType(
            { service.createRoutine(request(), "kakao", "user-1") },
            InvalidDietConditionException::class.java
        )
        assertThat(ex1).isNotNull
        assertThat(ex1.reason).isEqualTo("BMI_OUT_OF_RANGE")
        assertThat(ex1.description).isEqualTo("현재 BMI가 루틴 생성이 불가능한 범위입니다. (현재 BMI: 13.06, 범위: 16 이상 35 미만)")

        // 2. 고도 비만 BMI >= 35.0 차단
        val obeseProfile = UserProfileHistory(user = user(), heightCm = 175.0, weightKg = 110.0, gender = Gender.MALE)
        Mockito.`when`(profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1"))
            .thenReturn(obeseProfile)
        val ex2 = catchThrowableOfType(
            { service.createRoutine(request(), "kakao", "user-1") },
            InvalidDietConditionException::class.java
        )
        assertThat(ex2).isNotNull
        assertThat(ex2.reason).isEqualTo("BMI_OUT_OF_RANGE")
        assertThat(ex2.description).isEqualTo("현재 BMI가 루틴 생성이 불가능한 범위입니다. (현재 BMI: 35.92, 범위: 16 이상 35 미만)")

        // 3. 다이어트 목표 시 주당 1.5kg 초과 감량 차단
        val normalProfile = UserProfileHistory(user = user(), heightCm = 175.0, weightKg = 80.0, gender = Gender.MALE)
        Mockito.`when`(profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1"))
            .thenReturn(normalProfile)
        
        // 4주 동안 80kg -> 70kg 감량 시도 (주당 2.5kg 감량)
        val excessiveDietRequest = RoutineCreateRequest(
            fcmToken = "token",
            goal = GoalSection(goalType = GoalType.DIET, targetWeight = 70.0),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(totalWeeks = 4, hoursPerDay = 1.0, activeDays = listOf(DayOfWeek.MON)),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(locations = listOf(LocationType.HOME), equipments = emptyList())
        )
        val ex3 = catchThrowableOfType(
            { service.createRoutine(excessiveDietRequest, "kakao", "user-1") },
            InvalidDietConditionException::class.java
        )
        assertThat(ex3).isNotNull
        assertThat(ex3.reason).isEqualTo("WEEKLY_LOSS_LIMIT_EXCEEDED")
        assertThat(ex3.description).isEqualTo("주당 감량 목표가 1.5kg을 초과할 수 없습니다. (설정된 주당 감량: 2.50kg)")

        // 4. 다이어트 시 목표 체중이 현재 체중보다 크거나 같을 때 차단
        val invalidDietRequest = RoutineCreateRequest(
            fcmToken = "token",
            goal = GoalSection(goalType = GoalType.DIET, targetWeight = 85.0),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(totalWeeks = 4, hoursPerDay = 1.0, activeDays = listOf(DayOfWeek.MON)),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(locations = listOf(LocationType.HOME), equipments = emptyList())
        )
        val ex4 = catchThrowableOfType(
            { service.createRoutine(invalidDietRequest, "kakao", "user-1") },
            InvalidDietConditionException::class.java
        )
        assertThat(ex4).isNotNull
        assertThat(ex4.reason).isEqualTo("TARGET_WEIGHT_HIGHER_THAN_CURRENT")
        assertThat(ex4.description).isEqualTo("다이어트 목적의 목표 체중은 현재 체중보다 낮아야 합니다. (현재 체중: 80.0kg, 목표 체중: 85.0kg)")

        // 5. 다이어트 시 목표 체중 도달 시 예상 BMI < 16.0 차단
        // 25주 동안 80kg -> 45kg 감량 (주당 감량 = 35 / 25 = 1.4kg)
        // 예상 BMI = 45 / (1.75 * 1.75) = 14.69
        val lowTargetBmiRequest = RoutineCreateRequest(
            fcmToken = "token",
            goal = GoalSection(goalType = GoalType.DIET, targetWeight = 45.0),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(totalWeeks = 25, hoursPerDay = 1.0, activeDays = listOf(DayOfWeek.MON)),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(locations = listOf(LocationType.HOME), equipments = emptyList())
        )
        val ex5 = catchThrowableOfType(
            { service.createRoutine(lowTargetBmiRequest, "kakao", "user-1") },
            InvalidDietConditionException::class.java
        )
        assertThat(ex5).isNotNull
        assertThat(ex5.reason).isEqualTo("TARGET_BMI_OUT_OF_RANGE")
        assertThat(ex5.description).isEqualTo("목표 체중 도달 시 예상 BMI가 16 미만이 될 수 없습니다. (예상 BMI: 14.69)")
    }

    @Test
    fun `regenerateRoutine deletes old routine and triggers new creation when regeneration limit not exceeded`() {
        val user = user()
        val oldRoutine = Routine(
            id = 10L,
            user = user,
            totalWeeks = 4,
            originalRequestJson = jacksonObjectMapper().writeValueAsString(request()),
            regenerationCount = 0
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user)
        Mockito.`when`(routineRepository.findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc("kakao", "user-1"))
            .thenReturn(oldRoutine)

        service.regenerateRoutine("더 쉽게 해줘", "new-fcm-token", "kakao", "user-1")

        Mockito.verify(routineRepository).delete(oldRoutine)
        Mockito.verify(routineRepository).flush()

        val messageCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(rabbitTemplate).convertAndSend(
            Mockito.eq("routine.exchange"),
            Mockito.eq("routine.create.key"),
            messageCaptor.capture()
        )
        val message = jacksonObjectMapper().readValue<RoutineCreationMessage>(messageCaptor.value)
        assertThat(message.feedback).isEqualTo("더 쉽게 해줘")
        assertThat(message.regenerationCount).isEqualTo(1)
    }

    @Test
    fun `regenerateRoutine blocks when regeneration count exceeds limit`() {
        val user = user()
        val alreadyRegeneratedRoutine = Routine(
            id = 10L,
            user = user,
            totalWeeks = 4,
            originalRequestJson = jacksonObjectMapper().writeValueAsString(request()),
            regenerationCount = 1
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user)
        Mockito.`when`(routineRepository.findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc("kakao", "user-1"))
            .thenReturn(alreadyRegeneratedRoutine)

        assertThatThrownBy { service.regenerateRoutine("더 쉽게", "token", "kakao", "user-1") }
            .isInstanceOf(RegenerationLimitExceededException::class.java)
    }

    @Test
    fun `getWeeklyAchievementRates calculates rates correctly for each week`() {
        val user = user()
        val routine = Routine(
            id = 10L,
            user = user,
            totalWeeks = 2,
            startDate = LocalDate.of(2026, 5, 11)
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user)
        Mockito.`when`(routineRepository.findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc("kakao", "user-1"))
            .thenReturn(routine)

        // 1주차: 2026-05-11 ~ 2026-05-17
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "user-1", LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 17)))
            .thenReturn(10L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "user-1", LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 17)))
            .thenReturn(8L)

        // 2주차: 2026-05-18 ~ 2026-05-24
        Mockito.`when`(dailyWorkoutRepository.countExercisesBetweenDates("kakao", "user-1", LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 24)))
            .thenReturn(10L)
        Mockito.`when`(dailyWorkoutRepository.countCompletedExercisesBetweenDates("kakao", "user-1", LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 24)))
            .thenReturn(5L)

        val rates = service.getWeeklyAchievementRates("kakao", "user-1")

        assertThat(rates).hasSize(2)
        assertThat(rates[0].weekNumber).isEqualTo(1)
        assertThat(rates[0].achievementRate).isEqualTo(80.0)
        assertThat(rates[0].daysUntilNextRoutine).isNotNull()
        assertThat(rates[1].weekNumber).isEqualTo(2)
        assertThat(rates[1].achievementRate).isEqualTo(50.0)
        assertThat(rates[1].daysUntilNextRoutine).isNotNull()
    }
}
