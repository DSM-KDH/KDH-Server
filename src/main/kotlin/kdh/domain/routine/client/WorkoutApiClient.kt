package kdh.domain.routine.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import kdh.domain.routine.dto.Configurable
import kdh.domain.routine.dto.ExternalWorkoutApiRequest
import kdh.domain.routine.dto.ExternalWorkoutApiResponse
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.WorkoutApiConfig
import kdh.domain.routine.dto.WorkoutApiInput
import kdh.domain.routine.enum.BodyPart
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.UUID

@Component
class WorkoutApiClient(
    @Value("\${external.api.workout.url}") private val workoutApiUrl: String
) {
    private companion object {
        const val MAX_WORKOUT_API_RETRY_COUNT = 3
        const val RETRY_DELAY_MILLIS = 2_000L
    }

    private lateinit var webClient: WebClient
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        webClient = WebClient.builder().baseUrl(workoutApiUrl).build()
        log.info("Workout API client initialized. baseUrl={}", workoutApiUrl)
    }

    fun generateSingleWeekRoutine(request: RoutineCreateRequest, phase: Int): List<Map<String, Any>> {
        val threadId = UUID.randomUUID().toString()
        var currentState = createInitialState(request, phase, threadId)
        val weeklyWorkouts = mutableListOf<Map<String, Any>>()
        val targetWorkoutCount = request.schedule.activeDays.size
        val startedAt = System.currentTimeMillis()
        var generationAttempt = 1

        log.info(
            "Workout API weekly generation started. threadId={}, phase={}, targetWorkoutCount={}, activeDays={}, hoursPerDay={}, goalType={}, fitnessLevel={}, retryMax={}, retryBaseDelayMs={}",
            threadId,
            phase,
            targetWorkoutCount,
            request.schedule.activeDays,
            request.schedule.hoursPerDay,
            request.goal.goalType,
            request.fitnessLevel,
            MAX_WORKOUT_API_RETRY_COUNT,
            RETRY_DELAY_MILLIS
        )

        while (weeklyWorkouts.size < targetWorkoutCount) {
            val requestStartedAt = System.currentTimeMillis()
            log.info(
                "Calling Workout API. threadId={}, generationAttempt={}, currentDay={}, createdCount={}, targetWorkoutCount={}, requestBytes={}, currentWorkoutKeys={}, createdWorkoutCount={}",
                threadId,
                generationAttempt,
                currentState.input.day,
                weeklyWorkouts.size,
                targetWorkoutCount,
                objectMapper.writeValueAsBytes(currentState).size,
                currentState.input.current_workout.keys,
                currentState.input.created_workouts.size
            )

            val (responseJson, responseState) = requestWorkoutApiWithRetry(currentState, threadId, generationAttempt)
            log.info(
                "Workout API response received. threadId={}, generationAttempt={}, responseDay={}, done={}, hasCurrentWorkout={}, currentWorkoutSectionKeys={}, createdWorkoutCount={}, responseBytes={}, elapsedMs={}",
                threadId,
                generationAttempt,
                responseState.day,
                responseState.done,
                responseState.current_workout.isNotEmpty(),
                responseState.current_workout.keys,
                responseState.created_workouts.size,
                responseJson.toByteArray().size,
                System.currentTimeMillis() - requestStartedAt
            )

            if (responseState.current_workout.isNotEmpty()) {
                weeklyWorkouts.add(responseState.current_workout)
                log.info(
                    "Workout added to weekly result. threadId={}, generatedCount={}, targetWorkoutCount={}, sectionKeys={}",
                    threadId,
                    weeklyWorkouts.size,
                    targetWorkoutCount,
                    responseState.current_workout.keys
                )
            } else {
                log.warn(
                    "Workout API response had empty current_workout. threadId={}, generationAttempt={}, responseDay={}, done={}",
                    threadId,
                    generationAttempt,
                    responseState.day,
                    responseState.done
                )
            }

            if (responseState.done || weeklyWorkouts.size >= targetWorkoutCount) {
                log.info(
                    "Workout API weekly generation loop stopping. threadId={}, done={}, generatedCount={}, targetWorkoutCount={}",
                    threadId,
                    responseState.done,
                    weeklyWorkouts.size,
                    targetWorkoutCount
                )
                break
            }

            currentState = createNextState(responseState)
            generationAttempt += 1
        }

        log.info(
            "Workout API weekly generation finished. threadId={}, phase={}, generatedCount={}, targetWorkoutCount={}, elapsedMs={}",
            threadId,
            phase,
            weeklyWorkouts.size,
            targetWorkoutCount,
            System.currentTimeMillis() - startedAt
        )
        return weeklyWorkouts
    }

    private fun requestWorkoutApiWithRetry(
        state: ExternalWorkoutApiRequest,
        threadId: String,
        generationAttempt: Int
    ): Pair<String, WorkoutApiInput> {
        var lastError: Exception? = null

        for (retryAttempt in 1..MAX_WORKOUT_API_RETRY_COUNT) {
            val retryStartedAt = System.currentTimeMillis()
            try {
                log.info(
                    "Workout API retry attempt started. threadId={}, generationAttempt={}, retryAttempt={}/{}, apiPath={}, requestBytes={}",
                    threadId,
                    generationAttempt,
                    retryAttempt,
                    MAX_WORKOUT_API_RETRY_COUNT,
                    "/workout/invoke",
                    objectMapper.writeValueAsBytes(state).size
                )

                val responseJson = postToWorkoutApi(state)
                val responseState = objectMapper.readValue<ExternalWorkoutApiResponse>(responseJson).output
                log.info(
                    "Workout API call succeeded. threadId={}, generationAttempt={}, retryAttempt={}/{}, responseBytes={}, responseDay={}, done={}, currentWorkoutSections={}, elapsedMs={}",
                    threadId,
                    generationAttempt,
                    retryAttempt,
                    MAX_WORKOUT_API_RETRY_COUNT,
                    responseJson.toByteArray().size,
                    responseState.day,
                    responseState.done,
                    responseState.current_workout.keys,
                    System.currentTimeMillis() - retryStartedAt
                )
                return responseJson to responseState
            } catch (e: Exception) {
                lastError = e
                log.warn(
                    "Workout API call failed. threadId={}, generationAttempt={}, retryAttempt={}/{}, exceptionClass={}, message={}, elapsedMs={}",
                    threadId,
                    generationAttempt,
                    retryAttempt,
                    MAX_WORKOUT_API_RETRY_COUNT,
                    e.javaClass.name,
                    e.message,
                    System.currentTimeMillis() - retryStartedAt,
                    e
                )

                if (retryAttempt < MAX_WORKOUT_API_RETRY_COUNT) {
                    val delayMs = RETRY_DELAY_MILLIS * retryAttempt
                    log.info(
                        "Workout API retry scheduled. threadId={}, generationAttempt={}, nextRetryAttempt={}, delayMs={}",
                        threadId,
                        generationAttempt,
                        retryAttempt + 1,
                        delayMs
                    )
                    Thread.sleep(delayMs)
                }
            }
        }

        throw IllegalStateException(
            "Workout API 호출을 ${MAX_WORKOUT_API_RETRY_COUNT}회 모두 실패했습니다.",
            lastError
        )
    }

    private fun postToWorkoutApi(state: ExternalWorkoutApiRequest): String {
        return webClient.post()
            .uri("/workout/invoke")
            .bodyValue(state)
            .retrieve()
            .bodyToMono(String::class.java)
            .block() ?: throw IllegalStateException("Workout API 응답이 없습니다.")
    }

    private fun createInitialState(request: RoutineCreateRequest, phase: Int, threadId: String): ExternalWorkoutApiRequest {
        val extraCriteria = buildPrompt(request, phase)
        val input = WorkoutApiInput(
            day = 0,
            phase = phase,
            workouts_in_week = request.schedule.activeDays.size,
            workout_length = "${request.schedule.hoursPerDay}시간",
            extra_criteria = extraCriteria,
            client_info = "User(FCM Token: ${request.fcmToken})",
            current_workout = emptyMap(),
            created_workouts = emptyList(),
            user_feedback = "",
            done = false,
            thread_id = threadId
        )
        val config = WorkoutApiConfig(configurable = Configurable(thread_id = threadId))
        return ExternalWorkoutApiRequest(input = input, config = config)
    }

    private fun createNextState(prevState: WorkoutApiInput): ExternalWorkoutApiRequest {
        val input = prevState.copy(user_feedback = "CONTINUE")
        val config = WorkoutApiConfig(configurable = Configurable(thread_id = prevState.thread_id))
        return ExternalWorkoutApiRequest(input = input, config = config)
    }

    private fun buildPrompt(request: RoutineCreateRequest, phase: Int): String {
        val goalPrompt = when (request.goal.goalType) {
            GoalType.DIET -> "체중 감량을 목표로 하며, 목표 체중은 ${request.goal.targetWeight ?: "설정 안 함"}kg 입니다. 유산소와 근력 운동을 균형 있게 구성해주세요."
            GoalType.MUSCLE_GAIN -> "근비대와 근육 증가가 주 목적입니다. ${request.goal.targetBodyParts?.joinToString(", ") { it.toKorean() } ?: "전신"} 부위 발달에 집중해주세요."
            GoalType.HEALTH_CARE -> "건강 관리가 목표입니다. 전반적인 체력 향상과 균형 잡힌 루틴을 구성해주세요."
        }

        val phaseDescription = when (phase) {
            1 -> "Phase 1 안정화: 자세, 호흡, 코어, 기본 동작 패턴을 포함하고 부상 위험이 낮은 운동으로 구성해주세요."
            2 -> "Phase 2 근지구력: 무게와 횟수를 점진적으로 늘려 근지구력 향상에 초점을 맞춰주세요."
            3 -> "Phase 3 근육 발달: 부위별 분할 운동을 통해 특정 근육 그룹을 집중적으로 자극해주세요."
            else -> ""
        }

        return """
        ### 사용자 정보 및 목표
        - 운동 목표: $goalPrompt
        - 현재 수행 능력: ${request.fitnessLevel.toKorean()}
        - 운동 환경: ${request.environment.locations.joinToString(", ") { it.toKorean() }}
        - 사용 가능 기구: ${request.environment.equipments.joinToString(", ") { it.toKorean() }}
        - 선호 운동 유형: ${request.preferredExerciseTypes.joinToString(", ") { it.toKorean() }}

        ### 이번 주 훈련 지침
        - 훈련 단계: $phaseDescription
        - 주간 운동 일수: ${request.schedule.activeDays.size}일 (${request.schedule.activeDays.joinToString(", ") { it.toKorean() }})
        - 일일 운동 시간: ${request.schedule.hoursPerDay}시간

        ### 필수 제약 조건
        1. 최근 생성된 운동과 최대한 겹치지 않게 다양한 운동을 제안해주세요.
        2. 응답은 반드시 지정된 JSON 형식으로만 출력해주세요. 설명 문장은 포함하지 마세요.
        3. 모든 운동명과 값은 한국어로 작성해주세요.
        4. 각 운동은 exercise_name과 reps_time 필드를 포함해야 합니다.
        """.trimIndent()
    }

    private fun BodyPart.toKorean() = when (this) {
        BodyPart.CHEST -> "가슴"
        BodyPart.BACK -> "등"
        BodyPart.SHOULDER -> "어깨"
        BodyPart.ARM -> "팔"
        BodyPart.ABS -> "복근"
        BodyPart.THIGH -> "허벅지"
        BodyPart.CALF -> "종아리"
        BodyPart.HIP -> "엉덩이"
    }

    private fun FitnessLevel.toKorean() = when (this) {
        FitnessLevel.BEGINNER -> "초급자"
        FitnessLevel.INTERMEDIATE -> "중급자"
        FitnessLevel.ADVANCED -> "상급자"
    }

    private fun LocationType.toKorean() = when (this) {
        LocationType.OUTDOOR -> "야외"
        LocationType.GYM -> "헬스장"
        LocationType.HOME -> "집"
    }

    private fun EquipmentType.toKorean() = when (this) {
        EquipmentType.BARBELL -> "바벨"
        EquipmentType.DUMBBELL -> "덤벨"
        EquipmentType.CABLE -> "케이블"
        EquipmentType.MACHINE -> "머신"
        EquipmentType.BENCH -> "벤치"
        EquipmentType.PULL_UP_BAR -> "철봉"
        EquipmentType.BAND -> "밴드"
        EquipmentType.LAT_PULL_DOWN -> "랫 풀다운 머신"
        EquipmentType.SMITH_MACHINE -> "스미스 머신"
        EquipmentType.LEG_PRESS -> "레그 프레스"
        EquipmentType.PEC_DECK_FLY -> "펙덱 플라이 머신"
        EquipmentType.DIP_STATION -> "딥스 스테이션"
        EquipmentType.FOAM_ROLLER -> "폼롤러"
        EquipmentType.CYCLE -> "사이클"
    }

    private fun ExerciseType.toKorean() = when (this) {
        ExerciseType.CARDIO -> "유산소"
        ExerciseType.STRENGTH -> "근력"
        ExerciseType.BODYWEIGHT -> "맨몸"
    }

    private fun DayOfWeek.toKorean() = when (this) {
        DayOfWeek.MON -> "월"
        DayOfWeek.TUE -> "화"
        DayOfWeek.WED -> "수"
        DayOfWeek.THU -> "목"
        DayOfWeek.FRI -> "금"
        DayOfWeek.SAT -> "토"
        DayOfWeek.SUN -> "일"
    }
}
