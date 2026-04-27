package kdh.domain.routine.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import kdh.domain.routine.dto.*
import kdh.domain.routine.enum.*
import org.springframework.beans.factory.annotation.Value
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class WorkoutApiClient(
    @Value("\${external.api.workout.url}") private val workoutApiUrl: String
) {

    private lateinit var webClient: WebClient
    private val objectMapper: ObjectMapper = jacksonObjectMapper() // 자체적으로 ObjectMapper 인스턴스 생성
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
        var attempt = 1

        log.info(
            "Workout API weekly generation started. threadId={}, phase={}, targetWorkoutCount={}",
            threadId,
            phase,
            targetWorkoutCount
        )

        while (weeklyWorkouts.size < targetWorkoutCount) {
            val requestStartedAt = System.currentTimeMillis()
            log.info(
                "Calling Workout API. threadId={}, attempt={}, currentDay={}, createdCount={}",
                threadId,
                attempt,
                currentState.input.day,
                weeklyWorkouts.size
            )
            val responseJson = postToWorkoutApi(currentState)
            val responseState = objectMapper.readValue<ExternalWorkoutApiResponse>(responseJson).output
            log.info(
                "Workout API response received. threadId={}, attempt={}, responseDay={}, done={}, hasCurrentWorkout={}, responseBytes={}, elapsedMs={}",
                threadId,
                attempt,
                responseState.day,
                responseState.done,
                responseState.current_workout.isNotEmpty(),
                responseJson.toByteArray().size,
                System.currentTimeMillis() - requestStartedAt
            )

            if (responseState.current_workout.isNotEmpty()) {
                weeklyWorkouts.add(responseState.current_workout)
                log.info(
                    "Workout added to weekly result. threadId={}, generatedCount={}, sectionKeys={}",
                    threadId,
                    weeklyWorkouts.size,
                    responseState.current_workout.keys
                )
            }

            // Python graph는 생성 직후 interrupt되므로 done이 늦게 오거나 안 올 수 있다.
            // Kotlin 쪽에서는 요청한 운동 일수를 채우면 주간 생성이 끝난 것으로 본다.
            if (responseState.done || weeklyWorkouts.size >= targetWorkoutCount) {
                break
            }

            currentState = createNextState(responseState)
            attempt += 1
        }
        log.info(
            "Workout API weekly generation finished. threadId={}, phase={}, generatedCount={}, elapsedMs={}",
            threadId,
            phase,
            weeklyWorkouts.size,
            System.currentTimeMillis() - startedAt
        )
        return weeklyWorkouts
    }

    private fun postToWorkoutApi(state: ExternalWorkoutApiRequest): String {
        return webClient.post()
            .uri("/workout/invoke")
            .bodyValue(state)
            .retrieve()
            .bodyToMono<String>()
            .block() ?: throw IllegalStateException("API 응답이 없습니다.")
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
        val input = prevState.copy(
            user_feedback = "CONTINUE"
        )
        val config = WorkoutApiConfig(configurable = Configurable(thread_id = prevState.thread_id))
        return ExternalWorkoutApiRequest(input = input, config = config)
    }

    private fun buildPrompt(request: RoutineCreateRequest, phase: Int): String {
        val goalPrompt = when (request.goal.goalType) {
            GoalType.DIET -> "체중 감량을 목표로 하며, 목표 체중은 ${request.goal.targetWeight ?: "설정 안함"}kg 입니다. 전신 유산소와 근력 운동을 조화롭게 구성해주세요."
            GoalType.MUSCLE_GAIN -> "근비대(근육량 증가)가 주 목적입니다. 특히 ${request.goal.targetBodyParts?.joinToString(", ") { it.toKorean() } ?: "전신"} 부위의 발달에 집중하고 싶습니다."
            GoalType.HEALTH_CARE -> "건강 관리가 목표입니다. 전반적인 신체 능력을 향상시키는 균형잡힌 루틴을 원합니다."
        }

        val phaseDescription = when (phase) {
            1 -> "Phase 1 (안정화): 스쿼트, 힌지, 푸시, 프레스, 풀 5가지 기본 동작 패턴을 포함하되, 부상 위험이 적은 안정적인 운동들로 구성해주세요."
            2 -> "Phase 2 (근지구력): 무게와 횟수를 점진적으로 늘려 근지구력을 향상시키는 데 초점을 맞춰주세요."
            3 -> "Phase 3 (근육 발달): 부위별 분할 운동(예: 가슴/등/어깨, 삼두/이두/하체)을 통해 특정 근육 그룹을 집중적으로 자극해주세요."
            else -> ""
        }

        return """
        ### 사용자 정보 및 목표
        - **운동 목표**: $goalPrompt
        - **현재 수행 능력**: ${request.fitnessLevel.toKorean()}
        - **운동 환경**: 주로 ${request.environment.locations.joinToString(", ") { it.toKorean() }}에서 운동하며, 사용 가능한 기구는 [${request.environment.equipments.joinToString(", ") { it.toKorean() }}] 입니다.
        - **운동 선호**: ${request.preferredExerciseTypes.joinToString(", ") { it.toKorean() }} 종류의 운동을 선호합니다.

        ### 이번 주 훈련 지침
        - **훈련 단계(Phase)**: $phaseDescription
        - **주간 운동 일수**: ${request.schedule.activeDays.size}일 (${request.schedule.activeDays.joinToString(", ") { it.toKorean() }})
        - **일일 운동 시간**: ${request.schedule.hoursPerDay}시간

        ### 필수 제약 조건
        1. **다양성**: 3일 이내에 동일한 운동이 반복되지 않도록 최대한 다양한 운동을 제안해주세요.
        2. **JSON 형식**: 응답은 반드시 지정된 JSON 형식으로만 출력해야 합니다. 서론이나 부연 설명은 절대 포함하지 마세요.
        3. **과거 기록 참조**: 이전에 생성된 운동 목록을 참고하여 중복을 피해주세요.
        4. **한국어 답변**: 모든 운동 명칭, 설명, 값은 반드시 한국어로 작성해주세요.
        """.trimIndent()
    }

    // --- Enum to Korean Converters ---
    private fun BodyPart.toKorean() = when(this) {
        BodyPart.CHEST -> "가슴"
        BodyPart.BACK -> "등"
        BodyPart.SHOULDER -> "어깨"
        BodyPart.ARM -> "팔"
        BodyPart.ABS -> "복근"
        BodyPart.THIGH -> "허벅지"
        BodyPart.CALF -> "종아리"
        BodyPart.HIP -> "엉덩이"
    }

    private fun FitnessLevel.toKorean() = when(this) {
        FitnessLevel.BEGINNER -> "초급자"
        FitnessLevel.INTERMEDIATE -> "중급자"
        FitnessLevel.ADVANCED -> "상급자"
    }

    private fun LocationType.toKorean() = when(this) {
        LocationType.OUTDOOR -> "야외"
        LocationType.GYM -> "헬스장"
        LocationType.HOME -> "집"
    }

    private fun EquipmentType.toKorean() = when(this) {
        EquipmentType.BARBELL -> "바벨"
        EquipmentType.DUMBBELL -> "덤벨"
        EquipmentType.CABLE -> "케이블"
        EquipmentType.MACHINE -> "머신"
        EquipmentType.BENCH -> "벤치"
        EquipmentType.PULL_UP_BAR -> "철봉"
        EquipmentType.BAND -> "밴드"
        EquipmentType.LAT_PULL_DOWN -> "랫풀다운 머신"
        EquipmentType.SMITH_MACHINE -> "스미스 머신"
        EquipmentType.LEG_PRESS -> "레그 프레스"
        EquipmentType.PEC_DECK_FLY -> "펙덱 플라이 머신"
        EquipmentType.DIP_STATION -> "딥스 스테이션"
        EquipmentType.FOAM_ROLLER -> "폼롤러"
        EquipmentType.CYCLE -> "사이클"
    }
    
    private fun ExerciseType.toKorean() = when(this) {
        ExerciseType.CARDIO -> "유산소"
        ExerciseType.STRENGTH -> "근력"
        ExerciseType.BODYWEIGHT -> "맨몸"
    }

    private fun DayOfWeek.toKorean() = when(this) {
        DayOfWeek.MON -> "월"
        DayOfWeek.TUE -> "화"
        DayOfWeek.WED -> "수"
        DayOfWeek.THU -> "목"
        DayOfWeek.FRI -> "금"
        DayOfWeek.SAT -> "토"
        DayOfWeek.SUN -> "일"
    }
}
