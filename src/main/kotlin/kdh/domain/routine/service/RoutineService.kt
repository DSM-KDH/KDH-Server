package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.ExerciseCompletionResponse
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.routine.dto.RoutineAchievementRateResponse
import kdh.domain.routine.dto.RoutineDateResponse
import kdh.domain.routine.dto.RoutineDeleteResponse
import kdh.domain.routine.dto.RoutineWorkoutItemResponse
import kdh.domain.routine.dto.WeeklyAchievementRateResponse
import kdh.domain.routine.repository.DailyWorkoutRepository
import kdh.domain.routine.repository.ExerciseDetailRepository
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.exception.ExerciseCompletionDateInvalidException
import kdh.domain.routine.exception.ExerciseDeleteCompletedException
import kdh.domain.routine.exception.ExerciseDeletePastDateException
import kdh.domain.routine.exception.ExerciseNotFoundException
import kdh.domain.routine.exception.ExerciseWorkoutDateNotFoundException
import kdh.domain.routine.exception.FutureRoutineExistsException
import kdh.domain.routine.exception.InvalidDietConditionException
import kdh.domain.routine.exception.ProfileRequiredException
import kdh.domain.routine.exception.RegenerationLimitExceededException
import kdh.domain.routine.exception.RoutineNotFoundException
import kdh.domain.user.exception.UserNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class RoutineService(
    private val rabbitTemplate: RabbitTemplate,
    private val userRepository: UserRepository,
    private val userProfileHistoryRepository: UserProfileHistoryRepository,
    private val dailyWorkoutRepository: DailyWorkoutRepository,
    private val exerciseDetailRepository: ExerciseDetailRepository,
    private val routineRepository: RoutineRepository
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

        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)
        log.info("Routine creation owner validated. provider={}, providerId={}", provider, providerId)

        // FCM 토큰 업데이트
        if (request.fcmToken != null && request.fcmToken != user.fcmToken) {
            user.fcmToken = request.fcmToken
            userRepository.save(user)
        }

        val latestProfile = userProfileHistoryRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc(provider, providerId)
            ?: throw ProfileRequiredException()
        log.info("Routine creation profile history validated. provider={}, providerId={}", provider, providerId)

        // 신체 조건 및 다이어트 목표 유효성 검사 수행
        validateRoutineCreationCondition(request, latestProfile)

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

            throw FutureRoutineExistsException(firstFutureWorkoutDate)
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
        val workouts = dailyWorkoutRepository
            .findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate(provider, providerId, date)
            .flatMap { dailyWorkout ->
                dailyWorkout.sections.flatMap { section ->
                    section.exercises.map { exercise ->
                        RoutineWorkoutItemResponse.from(section, exercise)
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

    @Transactional(readOnly = true)
    fun getLastWeekAchievementRate(provider: String, providerId: String): RoutineAchievementRateResponse {
        val thisWeekMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startDate = thisWeekMonday.minusWeeks(1)
        val endDate = startDate.plusDays(6)
        val totalExerciseCount = dailyWorkoutRepository.countExercisesBetweenDates(
            provider = provider,
            providerId = providerId,
            startDate = startDate,
            endDate = endDate
        )
        val completedExerciseCount = dailyWorkoutRepository.countCompletedExercisesBetweenDates(
            provider = provider,
            providerId = providerId,
            startDate = startDate,
            endDate = endDate
        )
        val achievementRate = if (totalExerciseCount == 0L) {
            0.0
        } else {
            completedExerciseCount.toDouble() / totalExerciseCount.toDouble() * 100
        }

        log.info(
            "Last week routine achievement rate queried. provider={}, providerId={}, startDate={}, endDate={}, completedExerciseCount={}, totalExerciseCount={}, achievementRate={}",
            provider,
            providerId,
            startDate,
            endDate,
            completedExerciseCount,
            totalExerciseCount,
            achievementRate
        )

        return RoutineAchievementRateResponse(
            startDate = startDate,
            endDate = endDate,
            totalExerciseCount = totalExerciseCount,
            completedExerciseCount = completedExerciseCount,
            achievementRate = achievementRate
        )
    }

    @Transactional
    fun deleteFutureRoutines(provider: String, providerId: String): RoutineDeleteResponse {
        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)

        val today = LocalDate.now()
        val routines = routineRepository.findRoutinesWithFutureWorkouts(provider, providerId, today)
        val response = RoutineDeleteResponse(
            deletedRoutineCount = routines.size,
            deletedDailyWorkoutCount = routines.sumOf { it.dailyWorkouts.size },
            deletedWorkoutSectionCount = routines.sumOf { routine -> routine.dailyWorkouts.sumOf { it.sections.size } },
            deletedExerciseCount = routines.sumOf { routine ->
                routine.dailyWorkouts.sumOf { dailyWorkout ->
                    dailyWorkout.sections.sumOf { it.exercises.size }
                }
            }
        )

        if (routines.isNotEmpty()) {
            routineRepository.deleteAll(routines)
            log.info(
                "Future routines deleted. provider={}, providerId={}, today={}, deletedRoutineCount={}, deletedDailyWorkoutCount={}, deletedWorkoutSectionCount={}, deletedExerciseCount={}",
                provider,
                providerId,
                today,
                response.deletedRoutineCount,
                response.deletedDailyWorkoutCount,
                response.deletedWorkoutSectionCount,
                response.deletedExerciseCount
            )
        } else {
            log.info("No future routines to delete. provider={}, providerId={}, today={}", provider, providerId, today)
        }

        return response
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
            ?: throw ExerciseNotFoundException(exerciseId)

        val workoutDate = exercise.section?.dailyWorkout?.workoutDate
            ?: throw ExerciseWorkoutDateNotFoundException(exerciseId)

        if (workoutDate != LocalDate.now()) {
            throw ExerciseCompletionDateInvalidException()
        }

        exercise.completed = completed
        return ExerciseCompletionResponse(exerciseId = exercise.id, completed = exercise.completed)
    }

    @Transactional
    fun deleteExercise(exerciseId: Long, provider: String, providerId: String) {
        val exercise = exerciseDetailRepository
            .findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                exerciseId,
                provider,
                providerId
            )
            ?: throw ExerciseNotFoundException(exerciseId)

        val workoutDate = exercise.section?.dailyWorkout?.workoutDate
            ?: throw ExerciseWorkoutDateNotFoundException(exerciseId)

        if (workoutDate.isBefore(LocalDate.now())) {
            throw ExerciseDeletePastDateException()
        }

        if (exercise.completed) {
            throw ExerciseDeleteCompletedException()
        }

        val section = exercise.section
        if (section != null) {
            section.exercises.remove(exercise)
        }
        exerciseDetailRepository.delete(exercise)
    }


    @Transactional
    fun regenerateRoutine(feedback: String?, fcmToken: String?, provider: String, providerId: String) {
        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)

        // 최신 루틴을 조회한다.
        val latestRoutine = routineRepository.findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc(provider, providerId)
            ?: throw RoutineNotFoundException()

        if (latestRoutine.regenerationCount >= 1) {
            throw RegenerationLimitExceededException()
        }

        val originalRequestJson = latestRoutine.originalRequestJson
            ?: throw RoutineNotFoundException("이전 루틴 생성 조건 정보를 찾을 수 없어 재생성할 수 없습니다.")

        val originalRequest = objectMapper.readValue(originalRequestJson, RoutineCreateRequest::class.java)

        // 신규로 들어온 FCM 토큰이 있다면 반영 및 유저 테이블에도 업데이트
        val targetFcmToken = fcmToken ?: originalRequest.fcmToken
        if (targetFcmToken != null && targetFcmToken != user.fcmToken) {
            user.fcmToken = targetFcmToken
            userRepository.save(user)
        }

        val updatedRequest = originalRequest.copy(
            fcmToken = targetFcmToken
        )

        // 기존 루틴을 하드 삭제한다.
        routineRepository.delete(latestRoutine)
        
        // 영속성 컨텍스트를 플러시하여 충돌을 막음
        routineRepository.flush()

        // 새로운 루틴 생성을 큐에 등록한다. (재생성 횟수를 1 증가시킴)
        val messagePayload = RoutineCreationMessage(
            provider = provider,
            providerId = providerId,
            request = updatedRequest,
            feedback = feedback,
            regenerationCount = latestRoutine.regenerationCount + 1
        )
        val message = objectMapper.writeValueAsString(messagePayload)
        rabbitTemplate.convertAndSend("routine.exchange", "routine.create.key", message)
        log.info(
            "Routine regeneration request published. provider={}, providerId={}, feedback={}, regenerationCount={}",
            provider,
            providerId,
            feedback,
            latestRoutine.regenerationCount + 1
        )
    }

    @Transactional(readOnly = true)
    fun getWeeklyAchievementRates(provider: String, providerId: String): List<WeeklyAchievementRateResponse> {
        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)

        val latestRoutine = routineRepository.findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc(provider, providerId)
            ?: throw RoutineNotFoundException()

        val nextRoutineStartDate = latestRoutine.startDate.plusWeeks(latestRoutine.totalWeeks.toLong())
        val daysUntilNextRoutine = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), nextRoutineStartDate).coerceAtLeast(0L)

        return (1..latestRoutine.totalWeeks).map { week ->
            val startDate = latestRoutine.startDate.plusWeeks((week - 1).toLong())
            val endDate = startDate.plusDays(6)
            val totalCount = dailyWorkoutRepository.countExercisesBetweenDates(
                provider = provider,
                providerId = providerId,
                startDate = startDate,
                endDate = endDate
            )
            val completedCount = dailyWorkoutRepository.countCompletedExercisesBetweenDates(
                provider = provider,
                providerId = providerId,
                startDate = startDate,
                endDate = endDate
            )
            val rate = if (totalCount == 0L) 0.0 else completedCount.toDouble() / totalCount.toDouble() * 100

            WeeklyAchievementRateResponse(
                weekNumber = week,
                startDate = startDate,
                endDate = endDate,
                totalExerciseCount = totalCount,
                completedExerciseCount = completedCount,
                achievementRate = rate,
                daysUntilNextRoutine = daysUntilNextRoutine
            )
        }
    }

    @Transactional(readOnly = true)
    fun validateCreationConditionOnly(request: RoutineCreateRequest, provider: String, providerId: String) {
        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)

        val latestProfile = userProfileHistoryRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc(provider, providerId)
            ?: throw ProfileRequiredException()

        validateRoutineCreationCondition(request, latestProfile)
    }

    fun validateRoutineCreationCondition(
        request: RoutineCreateRequest,
        latestProfile: kdh.domain.user.entity.UserProfileHistory
    ) {
        val reasons = mutableListOf<String>()
        val descriptions = mutableListOf<String>()

        val heightM = latestProfile.heightCm / 100.0
        val currentBmi = latestProfile.weightKg / (heightM * heightM)
        if (currentBmi < 16.0 || currentBmi >= 35.0) {
            reasons.add("BMI_OUT_OF_RANGE")
            descriptions.add("현재 BMI가 루틴 생성이 불가능한 범위입니다. (현재 BMI: ${String.format(java.util.Locale.US, "%.2f", currentBmi)}, 범위: 16 이상 35 미만)")
        }

        if (request.goal.goalType == GoalType.DIET) {
            val targetWeight = request.goal.targetWeight 
            if (targetWeight == null) {
                reasons.add("TARGET_WEIGHT_REQUIRED")
                descriptions.add("다이어트 목표인 경우 목표 체중을 입력해야 합니다.")
            } else {
                if (targetWeight >= latestProfile.weightKg) {
                    reasons.add("TARGET_WEIGHT_HIGHER_THAN_CURRENT")
                    descriptions.add("다이어트 목적의 목표 체중은 현재 체중보다 낮아야 합니다. (현재 체중: ${latestProfile.weightKg}kg, 목표 체중: ${targetWeight}kg)")
                }

                val lossPerWeek = (latestProfile.weightKg - targetWeight) / request.schedule.totalWeeks
                if (lossPerWeek > 1.5) {
                    reasons.add("WEEKLY_LOSS_LIMIT_EXCEEDED")
                    descriptions.add("주당 감량 목표가 1.5kg을 초과할 수 없습니다. (설정된 주당 감량: ${String.format(java.util.Locale.US, "%.2f", lossPerWeek)}kg)")
                }

                val targetBmi = targetWeight / (heightM * heightM)
                if (targetBmi < 16.0) {
                    reasons.add("TARGET_BMI_OUT_OF_RANGE")
                    descriptions.add("목표 체중 도달 시 예상 BMI가 16 미만이 될 수 없습니다. (예상 BMI: ${String.format(java.util.Locale.US, "%.2f", targetBmi)})")
                }
            }
        }

        if (reasons.isNotEmpty()) {
            throw InvalidDietConditionException(
                reason = reasons.joinToString(", "),
                description = descriptions.joinToString("; ")
            )
        }
    }
}
