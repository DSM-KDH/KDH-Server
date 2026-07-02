package kdh.domain.routine.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.captureValue
import kdh.domain.routine.client.WorkoutApiClient
import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.ScheduleSection
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.infra.fcm.FcmService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@DataJpaTest(
    showSql = false,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:routine-creation-flow-test;NON_KEYWORDS=DAY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    RoutineService::class,
    RoutineMessageHandler::class,
    RoutineGenerationService::class,
    RoutineCreationTracker::class,
    RoutineCreationFlowIntegrationTest.MockConfig::class
)
class RoutineCreationFlowIntegrationTest @Autowired constructor(
    private val routineService: RoutineService,
    private val routineMessageHandler: RoutineMessageHandler,
    private val workoutApiClient: WorkoutApiClient,
    private val fcmService: FcmService,
    private val rabbitTemplate: RabbitTemplate,
    private val entityManager: TestEntityManager
) {

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        Mockito.reset(workoutApiClient, fcmService, rabbitTemplate)
    }

    @Test
    fun `create routine then query dates and detail returns generated workout data`() {
        val user = entityManager.persist(User(provider = "kakao", providerId = "user-1", name = "Tester"))
        entityManager.persist(
            UserProfileHistory(
                user = user,
                heightCm = 175.0,
                weightKg = 70.0,
                gender = Gender.MALE
            )
        )
        flushAndClear()

        val request = request()
        val generatedWeek = listOf(
            workout(
                warmUpExercise = "제자리 걷기 March in place",
                strengthExercise = "밴드 스쿼트 Band squat",
                cooldownExercise = "스트레칭 Hamstring stretch"
            ),
            workout(
                warmUpExercise = "팔 돌리기 Arm circles",
                strengthExercise = "벽 푸시업 Wall push up",
                cooldownExercise = "종아리 스트레칭 Calf stretch"
            )
        )
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:user-1", null)).thenReturn(listOf(generatedWeek))

        routineService.createRoutine(request, "kakao", "user-1")
        val publishedMessage = capturePublishedMessage()

        routineMessageHandler.handleMessage(publishedMessage)
        flushAndClear()

        val routineDates = routineService.getMyRoutineDates("kakao", "user-1")
        assertThat(routineDates).hasSize(2)
        assertThat(routineDates).containsExactlyElementsOf(routineDates.sorted())

        val firstDetail = routineService.getMyRoutineByDate(routineDates.first(), "kakao", "user-1")
        assertThat(firstDetail.date).isEqualTo(routineDates.first())
        assertThat(firstDetail.workouts).hasSize(3)
        assertThat(firstDetail.workouts).extracting<String> { it.sectionName }
            .containsExactly("Warm up", "Strength", "Cooldown")
        assertThat(firstDetail.workouts).extracting<String> { it.exerciseName }
            .containsExactly("제자리 걷기 March in place", "밴드 스쿼트 Band squat", "스트레칭 Hamstring stretch")
        assertThat(firstDetail.workouts).extracting<String?> { it.repsTime }
            .containsExactly("5 min", "12 reps", "5 min")
        assertThat(firstDetail.workouts).allSatisfy { workout ->
            assertThat(workout.exerciseId).isPositive()
            assertThat(workout.completed).isFalse()
        }

        val secondDetail = routineService.getMyRoutineByDate(routineDates.last(), "kakao", "user-1")
        assertThat(secondDetail.workouts).extracting<String> { it.exerciseName }
            .containsExactly("팔 돌리기 Arm circles", "벽 푸시업 Wall push up", "종아리 스트레칭 Calf stretch")
        val progressCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        Mockito.verify(fcmService, Mockito.times(3)).sendNotification(
            Mockito.eq("fcm-token"),
            Mockito.anyString(),
            Mockito.anyString(),
            captureValue(progressCaptor)
        )
        assertThat(progressCaptor.allValues.last())
            .containsEntry("status", "COMPLETED")
            .containsEntry("phase", "COMPLETED")
            .containsEntry("createdCount", "2")
            .containsEntry("totalCount", "2")
            .containsEntry("progressPercent", "100")
            .containsEntry("estimatedRemainingMinutes", "0")
    }

    @Test
    fun `deleteFutureRoutines removes generated routine with future workouts`() {
        val user = entityManager.persist(User(provider = "kakao", providerId = "delete-user", name = "Delete Tester"))
        entityManager.persist(
            UserProfileHistory(
                user = user,
                heightCm = 175.0,
                weightKg = 70.0,
                gender = Gender.MALE
            )
        )
        flushAndClear()

        val request = request()
        val generatedWeek = listOf(
            workout(
                warmUpExercise = "제자리 걷기 March in place",
                strengthExercise = "밴드 스쿼트 Band squat",
                cooldownExercise = "스트레칭 Hamstring stretch"
            ),
            workout(
                warmUpExercise = "팔 돌리기 Arm circles",
                strengthExercise = "벽 푸시업 Wall push up",
                cooldownExercise = "종아리 스트레칭 Calf stretch"
            )
        )
        Mockito.`when`(workoutApiClient.generateMultiWeekRoutine(request, "kakao:delete-user", null)).thenReturn(listOf(generatedWeek))

        routineService.createRoutine(request, "kakao", "delete-user")
        routineMessageHandler.handleMessage(capturePublishedMessage())
        flushAndClear()

        assertThat(routineService.getMyRoutineDates("kakao", "delete-user")).hasSize(2)

        val response = routineService.deleteFutureRoutines("kakao", "delete-user")
        flushAndClear()

        assertThat(response.deletedRoutineCount).isEqualTo(1)
        assertThat(response.deletedDailyWorkoutCount).isEqualTo(2)
        assertThat(response.deletedWorkoutSectionCount).isEqualTo(6)
        assertThat(response.deletedExerciseCount).isEqualTo(6)
        assertThat(routineService.getMyRoutineDates("kakao", "delete-user")).isEmpty()
    }

    private fun capturePublishedMessage(): String {
        val messageCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(rabbitTemplate).convertAndSend(
            Mockito.eq("routine.exchange"),
            Mockito.eq("routine.create.key"),
            messageCaptor.capture()
        )
        objectMapper.readTree(messageCaptor.value)
        return messageCaptor.value
    }

    private fun request(): RoutineCreateRequest {
        return RoutineCreateRequest(
            fcmToken = "fcm-token",
            goal = GoalSection(goalType = GoalType.HEALTH_CARE),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(
                totalWeeks = 1,
                hoursPerDay = 1.0,
                activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED)
            ),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT, ExerciseType.STRENGTH),
            environment = EnvironmentSection(
                locations = listOf(LocationType.HOME),
                equipments = listOf(EquipmentType.BAND, EquipmentType.FOAM_ROLLER)
            )
        )
    }

    private fun workout(
        warmUpExercise: String,
        strengthExercise: String,
        cooldownExercise: String
    ): Map<String, Any> {
        return mapOf(
            "1. Warm up" to listOf(
                mapOf("exercise_name" to warmUpExercise, "reps_time" to "5 min")
            ),
            "Strength portion" to listOf(
                mapOf("exercise_name" to strengthExercise, "reps_time" to "12 reps")
            ),
            "Cooldown" to listOf(
                mapOf("exercise_name" to cooldownExercise, "reps_time" to "5 min")
            )
        )
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @TestConfiguration
    class MockConfig {
        @Bean
        fun rabbitTemplate(): RabbitTemplate = Mockito.mock(RabbitTemplate::class.java)

        @Bean
        fun workoutApiClient(): WorkoutApiClient = Mockito.mock(WorkoutApiClient::class.java)

        @Bean
        fun fcmService(): FcmService = Mockito.mock(FcmService::class.java)
    }
}
