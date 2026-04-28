package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.ExerciseCompletionResponse
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.routine.dto.RoutineDateResponse
import kdh.domain.routine.dto.RoutineWorkoutItemResponse
import kdh.domain.routine.repository.DailyWorkoutRepository
import kdh.domain.routine.repository.ExerciseDetailRepository
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RoutineService(
    private val rabbitTemplate: RabbitTemplate,
    private val userRepository: UserRepository,
    private val userProfileHistoryRepository: UserProfileHistoryRepository,
    private val dailyWorkoutRepository: DailyWorkoutRepository,
    private val exerciseDetailRepository: ExerciseDetailRepository
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    fun createRoutine(request: RoutineCreateRequest, provider: String, providerId: String) {
        log.info(
            "Routine creation request validation started. provider={}, providerId={}, totalWeeks={}, activeDays={}, hoursPerDay={}, goalType={}, targetWeight={}, targetBodyParts={}, fitnessLevel={}, preferredExerciseTypes={}, locations={}, equipments={}",
            provider,
            providerId,
            request.schedule.totalWeeks,
            request.schedule.activeDays,
            request.schedule.hoursPerDay,
            request.goal.goalType,
            request.goal.targetWeight,
            request.goal.targetBodyParts,
            request.fitnessLevel,
            request.preferredExerciseTypes,
            request.environment.locations,
            request.environment.equipments
        )

        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $provider/$providerId")
        log.info("Routine creation owner validated. provider={}, providerId={}", provider, providerId)

        if (!userProfileHistoryRepository.existsByUserProviderAndUserProviderId(provider, providerId)) {
            throw IllegalArgumentException("신체 정보가 없습니다. 키, 몸무게, 성별을 먼저 등록해야 루틴을 생성할 수 있습니다.")
        }
        log.info("Routine creation profile history validated. provider={}, providerId={}", provider, providerId)

        val today = LocalDate.now()
        val firstFutureWorkoutDate = dailyWorkoutRepository.findFirstFutureWorkoutDate(
            provider = provider,
            providerId = providerId,
            today = today
        )
        if (firstFutureWorkoutDate != null) {
            log.warn(
                "Routine creation blocked because future routine exists. provider={}, providerId={}, today={}, firstFutureWorkoutDate={}",
                provider,
                providerId,
                today,
                firstFutureWorkoutDate
            )
            throw IllegalArgumentException("현재일 이후에 예정된 루틴이 있어 새 루틴을 생성할 수 없습니다. 가장 가까운 예정일: $firstFutureWorkoutDate")
        }
        log.info(
            "Routine creation future routine validation passed. provider={}, providerId={}, today={}",
            provider,
            providerId,
            today
        )

        val messagePayload = RoutineCreationMessage(provider = provider, providerId = providerId, request = request)
        val message = objectMapper.writeValueAsString(messagePayload)
        rabbitTemplate.convertAndSend("routine.exchange", "routine.create.key", message)
        log.info(
            "Routine creation message published. provider={}, providerId={}, exchange={}, routingKey={}, payloadBytes={}",
            provider,
            providerId,
            "routine.exchange",
            "routine.create.key",
            message.toByteArray().size
        )
    }

    @Transactional(readOnly = true)
    fun getMyRoutineByDate(date: LocalDate, provider: String, providerId: String): RoutineDateResponse {
        val canComplete = date == LocalDate.now()
        val workouts = dailyWorkoutRepository
            .findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate(provider, providerId, date)
            .flatMap { dailyWorkout ->
                dailyWorkout.sections.flatMap { section ->
                    section.exercises.map { exercise ->
                        RoutineWorkoutItemResponse.from(section, exercise, canComplete)
                    }
                }
            }

        return RoutineDateResponse(date = date, workouts = workouts)
    }

    @Transactional(readOnly = true)
    fun getMyRoutineDates(provider: String, providerId: String): List<LocalDate> {
        val today = LocalDate.now()
        val fromDate = today.minusMonths(1)
        val dates = dailyWorkoutRepository.findDistinctWorkoutDatesFrom(
            provider = provider,
            providerId = providerId,
            fromDate = fromDate
        )
        log.info(
            "Routine date list queried. provider={}, providerId={}, fromDate={}, resultCount={}, dates={}",
            provider,
            providerId,
            fromDate,
            dates.size,
            dates
        )
        return dates
    }

    @Transactional
    fun updateExerciseCompletion(
        exerciseId: Long,
        completed: Boolean,
        provider: String,
        providerId: String
    ): ExerciseCompletionResponse {
        val exercise = exerciseDetailRepository
            .findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                exerciseId,
                provider,
                providerId
            )
            ?: throw IllegalArgumentException("운동을 찾을 수 없습니다: $exerciseId")

        val workoutDate = exercise.section?.dailyWorkout?.workoutDate
            ?: throw IllegalArgumentException("운동 날짜를 찾을 수 없습니다: $exerciseId")

        if (workoutDate != LocalDate.now()) {
            throw IllegalArgumentException("당일 운동만 완료 처리할 수 있습니다.")
        }

        exercise.completed = completed
        return ExerciseCompletionResponse(exerciseId = exercise.id, completed = exercise.completed)
    }
}
