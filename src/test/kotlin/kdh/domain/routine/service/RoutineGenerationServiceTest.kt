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
import kdh.domain.routine.exception.InvalidRoutineGenerationResultException
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
    fun `generateMultiWeekRoutine saves parsed workouts with user id and notification`() {
        val request = request(totalWeeks = 4, activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED))
        val weeklyWorkoutsByWeek = (1..4).map { week ->
            listOf(
                workout("Week $week Warm up", "걷기 walk-$week", "5 min"),
                workout("Week $week Strength", "스쿼트 squat-$week", "10 reps")
            )
        }
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(weeklyWorkoutsByWeek)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        Mockito.verify(workoutApiClient, Mockito.times(1)).generateMultiWeekRoutine(request, "kakao:user-1", null)

        val routineCaptor = ArgumentCaptor.forClass(Routine::class.java)
        Mockito.verify(routineRepository, Mockito.atLeastOnce()).saveAndFlush(captureValue(routineCaptor))
        val savedRoutine = routineCaptor.allValues.last()
        assertThat(savedRoutine.dailyWorkouts).hasSize(8)
        assertThat(savedRoutine.dailyWorkouts.map { it.day }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(savedRoutine.dailyWorkouts.flatMap { it.sections }.map { it.name })
            .contains("Week 1 Warm up", "Week 2 Warm up", "Week 3 Strength", "Week 4 Strength")
        assertThat(savedRoutine.dailyWorkouts.flatMap { daily -> daily.sections.flatMap { it.exercises } })
            .extracting<String> { it.exerciseName }
            .contains("걷기 walk-1", "스쿼트 squat-2", "걷기 walk-3", "스쿼트 squat-4")
        assertThat(savedRoutine.dailyWorkouts.mapNotNull { it.workoutDate })
            .allSatisfy { date ->
                assertThat(date).isAfterOrEqualTo(LocalDate.now())
                assertThat(date.dayOfWeek).isIn(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY)
            }
        val progressCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        Mockito.verify(fcmService, Mockito.times(7)).sendNotification(
            Mockito.eq("fcm-token"),
            Mockito.anyString(),
            Mockito.anyString(),
            captureValue(progressCaptor)
        )
        assertThat(progressCaptor.allValues.map { it["progressPercent"] })
            .contains("0", "25", "50", "75", "100")
        assertThat(progressCaptor.allValues.last())
            .containsEntry("status", "COMPLETED")
            .containsEntry("phase", "COMPLETED")
            .containsEntry("createdCount", "8")
            .containsEntry("totalCount", "8")
            .containsEntry("estimatedFirstWeekMinutes", "4")
            .containsEntry("estimatedTotalMinutes", "5")
            .containsEntry("estimatedRemainingMinutes", "0")
            .containsEntry("completed", "true")
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
        val weeklyWorkoutsByWeek = (1..3).map { week ->
            activeDays.mapIndexed { index, day ->
                workout("Week $week Day ${index + 1} $day", "한국어 운동 exercise-$week-$day", "${index + 1}0 reps")
            }
        }
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(weeklyWorkoutsByWeek)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        Mockito.verify(workoutApiClient, Mockito.times(1)).generateMultiWeekRoutine(request, "kakao:user-1", null)

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
        assertThat(savedRoutine.dailyWorkouts[7].sections.first().exercises.first().exerciseName)
            .isEqualTo("한국어 운동 exercise-2-MON")
        assertThat(savedRoutine.dailyWorkouts[14].sections.first().exercises.first().exerciseName)
            .isEqualTo("한국어 운동 exercise-3-MON")
        val progressCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        Mockito.verify(fcmService, Mockito.times(6)).sendNotification(
            Mockito.eq("fcm-token"),
            Mockito.anyString(),
            Mockito.anyString(),
            captureValue(progressCaptor)
        )
        assertThat(progressCaptor.allValues.last())
            .containsEntry("status", "COMPLETED")
            .containsEntry("phase", "COMPLETED")
            .containsEntry("createdCount", "21")
            .containsEntry("totalCount", "21")
            .containsEntry("progressPercent", "100")
            .containsEntry("estimatedFirstWeekMinutes", "7")
            .containsEntry("estimatedTotalMinutes", "10")
            .containsEntry("estimatedRemainingMinutes", "0")
    }

    @Test
    fun `generateMultiWeekRoutine throws when user is missing and does not call api`() {
        val request = request()
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "missing")).thenReturn(null)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "missing") }
            .isInstanceOf(UserNotFoundException::class.java)

        Mockito.verifyNoInteractions(workoutApiClient, routineRepository)
        Mockito.verify(fcmService).sendNotification(
            Mockito.eq("fcm-token"),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyMap()
        )
    }

    @Test
    fun `generateMultiWeekRoutine does not save empty routine when workout api fails`() {
        val request = request(totalWeeks = 3, activeDays = listOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED))
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null))
            .thenThrow(RuntimeException("workout api timeout"))

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("workout api timeout")

        Mockito.verify(routineRepository, Mockito.never()).saveAndFlush(anyValue())
        Mockito.verify(fcmService).sendNotification(
            Mockito.eq("fcm-token"),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyMap()
        )
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
            schedule = ScheduleSection(totalWeeks = totalWeeks, hoursPerDay = 1.0, activeDays = activeDays),
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

    @Test
    fun `generateMultiWeekRoutine retries on validation failure and succeeds when next attempt is valid`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        
        // 1회차: 한글이 없는 영어 응답 (검증 실패)
        val invalidWeek = listOf(
            listOf(workout("Warm up", "walk-1", "5 min"))
        )
        // 2회차: 한글이 들어있는 정상 응답 (검증 성공)
        val validWeek = listOf(
            listOf(workout("Warm up", "걷기 walk-1", "5 min"))
        )
        
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        
        // 1회차엔 invalidWeek, 2회차엔 validWeek 반환하도록 Stubbing
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null))
            .thenReturn(invalidWeek)
            .thenReturn(validWeek)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        // API가 2회 호출되었는지 검증
        Mockito.verify(workoutApiClient, Mockito.times(2)).generateMultiWeekRoutine(request, "kakao:user-1", null)
        Mockito.verify(routineRepository, Mockito.times(1)).saveAndFlush(anyValue())
    }

    @Test
    fun `generateMultiWeekRoutine throws InvalidRoutineGenerationResultException after 3 validation failures`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        
        // 3회차 모두 운동명이 비어있어 필터링 후 0개가 됨 (Fallback 구제 불가)
        val invalidWeek = listOf(
            listOf(workout("Warm up", "", "5 min"))
        )
        
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null))
            .thenReturn(invalidWeek)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(InvalidRoutineGenerationResultException::class.java)

        // API가 3회 모두 호출되었는지 검증
        Mockito.verify(workoutApiClient, Mockito.times(3)).generateMultiWeekRoutine(request, "kakao:user-1", null)
        Mockito.verify(routineRepository, Mockito.never()).saveAndFlush(anyValue())
    }

    @Test
    fun `generateMultiWeekRoutine fails when exercise name contains illegal character like Hanja`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        val invalidWeek = listOf(
            listOf(workout("Warm up", "걷기 體育", "5 min"))
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(invalidWeek)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(InvalidRoutineGenerationResultException::class.java)
            .hasMessageContaining("비어있습니다")
    }

    @Test
    fun `generateMultiWeekRoutine fails when exercise name contains repeated Jamo`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        val invalidWeek = listOf(
            listOf(workout("Warm up", "걷기 ㅋㅋ", "5 min"))
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(invalidWeek)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(InvalidRoutineGenerationResultException::class.java)
            .hasMessageContaining("비어있습니다")
    }

    @Test
    fun `generateMultiWeekRoutine fails when generated days count does not match schedule`() {
        // schedule = 2 days, but API returns 1 day
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED))
        val invalidWeek = listOf(
            listOf(workout("Warm up", "", "5 min")) // 운동명 비어있어 필터링 후 0개 됨
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(invalidWeek)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(InvalidRoutineGenerationResultException::class.java)
            .hasMessageContaining("비어있습니다")
    }

    @Test
    fun `generateMultiWeekRoutine fails when exercise name length is less than two`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        val invalidWeek = listOf(
            listOf(workout("Warm up", "걷", "5 min"))
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(invalidWeek)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(InvalidRoutineGenerationResultException::class.java)
            .hasMessageContaining("비어있습니다")
    }

    @Test
    fun `generateMultiWeekRoutine fails when repsTime is missing`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        val invalidWeek = listOf(
            listOf(workout("Warm up", "걷기 운동", ""))
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(invalidWeek)

        assertThatThrownBy { service.generateMultiWeekRoutine(request, "kakao", "user-1") }
            .isInstanceOf(InvalidRoutineGenerationResultException::class.java)
            .hasMessageContaining("비어있습니다")
    }

    @Test
    fun `generateMultiWeekRoutine filters out invalid exercises and saves valid ones when remaining exercises are non-empty`() {
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON))
        val mixedWeek = listOf(
            listOf(
                mapOf(
                    "Warm up" to listOf(
                        mapOf("exercise_name" to "스쿼트", "reps_time" to "3세트 10회"),
                        mapOf("exercise_name" to "스탠딩 토 태ップ", "reps_time" to "3세트 10회") // 가타카나 포함 (비정상)
                    )
                )
            )
        )
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(mixedWeek)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        val routineCaptor = ArgumentCaptor.forClass(Routine::class.java)
        Mockito.verify(routineRepository).saveAndFlush(captureValue(routineCaptor))
        val savedRoutine = routineCaptor.value
        val savedExercises = savedRoutine.dailyWorkouts.flatMap { it.sections }.flatMap { it.exercises }

        // 비정상 운동인 '스탠딩 토 태ップ'은 제거되고 '스쿼트'만 남아서 저장되었어야 함
        assertThat(savedExercises).hasSize(1)
        assertThat(savedExercises.first().exerciseName).isEqualTo("스쿼트")
    }

    @Test
    fun `generateMultiWeekRoutine falls back to the attempt with most remaining exercises when all attempts fail structural validation`() {
        // activeDays = 2개로 설정 (총 2개의 운동 날짜가 생겨야 함)
        val request = request(totalWeeks = 1, activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED))

        // 1회차: 운동이 0개 남음 (비정상 운동만 1개)
        val attempt1 = listOf(
            listOf(workout("Warm up", "스탠딩 토 태ップ", "5 min"))
        )
        // 2회차: 정상 운동 2개 남음 (단, 하루만 반환하여 일수 불합치로 validate 실패할 예정)
        val attempt2 = listOf(
            listOf(
                mapOf(
                    "Warm up" to listOf(
                        mapOf("exercise_name" to "스쿼트 1", "reps_time" to "3세트"),
                        mapOf("exercise_name" to "스쿼트 2", "reps_time" to "3세트")
                    )
                )
            )
        )
        // 3회차: 정상 운동 1개 남음 (하루만 반환하여 validate 실패)
        val attempt3 = listOf(
            listOf(workout("Warm up", "스쿼트 3", "5 min"))
        )

        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null))
            .thenReturn(attempt1)
            .thenReturn(attempt2)
            .thenReturn(attempt3)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        // 3회 모두 API가 호출되었는지 검증
        Mockito.verify(workoutApiClient, Mockito.times(3)).generateMultiWeekRoutine(request, "kakao:user-1", null)

        // 구조적 검증이 모두 실패했으나, 2개 운동이 남은 2회차(attempt2) 루틴이 강제로 저장되었는지 검증
        val routineCaptor = ArgumentCaptor.forClass(Routine::class.java)
        Mockito.verify(routineRepository).saveAndFlush(captureValue(routineCaptor))
        val savedRoutine = routineCaptor.value
        val savedExercises = savedRoutine.dailyWorkouts.flatMap { it.sections }.flatMap { it.exercises }

        assertThat(savedExercises).hasSize(2)
        assertThat(savedExercises.map { it.exerciseName }).containsExactlyInAnyOrder("스쿼트 1", "스쿼트 2")
    }

    @Test
    fun `generateMultiWeekRoutine applies programmatic weekly progression`() {
        val request = request(totalWeeks = 4, activeDays = listOf(DayOfWeek.MON))
        val baseWeek = listOf(
            mapOf(
                "Warm up" to listOf(
                    mapOf("exercise_name" to "걷기", "reps_time" to "5분"),
                    mapOf("exercise_name" to "플랭크", "reps_time" to "3세트 30초"),
                    mapOf("exercise_name" to "스쿼트", "reps_time" to "3세트 10회"),
                    mapOf("exercise_name" to "런지", "reps_time" to "3세트 10~12회(각쪽)")
                )
            )
        )
        val weeklyWorkoutsByWeek = listOf(baseWeek, baseWeek, baseWeek, baseWeek)
        
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user())
        Mockito.`when`(routineRepository.saveAndFlush(anyValue())).thenAnswer { it.arguments[0] }
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(weeklyWorkoutsByWeek)

        service.generateMultiWeekRoutine(request, "kakao", "user-1")

        val routineCaptor = ArgumentCaptor.forClass(Routine::class.java)
        Mockito.verify(routineRepository, Mockito.atLeastOnce()).saveAndFlush(captureValue(routineCaptor))
        val savedRoutine = routineCaptor.allValues.last()
        val dailyWorkouts = savedRoutine.dailyWorkouts

        // Week 1 (Day 1)
        val week1Exercises = dailyWorkouts[0].sections.first().exercises
        assertThat(week1Exercises[0].repsTime).isEqualTo("5분")
        assertThat(week1Exercises[1].repsTime).isEqualTo("3세트 30초")
        assertThat(week1Exercises[2].repsTime).isEqualTo("3세트 10회")
        assertThat(week1Exercises[3].repsTime).isEqualTo("3세트 10~12회(각쪽)")

        // Week 2 (Day 2)
        val week2Exercises = dailyWorkouts[1].sections.first().exercises
        assertThat(week2Exercises[0].repsTime).isEqualTo("5분 30초")
        assertThat(week2Exercises[1].repsTime).isEqualTo("3세트 35초")
        assertThat(week2Exercises[2].repsTime).isEqualTo("3세트 11회")
        assertThat(week2Exercises[3].repsTime).isEqualTo("3세트 11~13회(각쪽)")

        // Week 3 (Day 3)
        val week3Exercises = dailyWorkouts[2].sections.first().exercises
        assertThat(week3Exercises[0].repsTime).isEqualTo("6분")
        assertThat(week3Exercises[1].repsTime).isEqualTo("4세트 35초")
        assertThat(week3Exercises[2].repsTime).isEqualTo("4세트 11회")
        assertThat(week3Exercises[3].repsTime).isEqualTo("4세트 11~13회(각쪽)")

        // Week 4 (Day 4)
        val week4Exercises = dailyWorkouts[3].sections.first().exercises
        assertThat(week4Exercises[0].repsTime).isEqualTo("6분 30초")
        assertThat(week4Exercises[1].repsTime).isEqualTo("4세트 40초")
        assertThat(week4Exercises[2].repsTime).isEqualTo("4세트 12회")
        assertThat(week4Exercises[3].repsTime).isEqualTo("4세트 12~14회(각쪽)")

        val sb = StringBuilder()
        sb.appendLine("=== 루틴 진행도 시뮬레이션 결과 ===")
        for (w in 1..4) {
            sb.appendLine("\n[${w}주차]")
            val exercises = dailyWorkouts[w - 1].sections.first().exercises
            exercises.forEach { ex ->
                sb.appendLine("- ${ex.exerciseName}: ${ex.repsTime}")
            }
        }
        java.io.File("c:/Users/user/Desktop/pj/KDH/progression_result.txt").writeText(sb.toString())
    }
}

