package kdh.domain.routine.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// 1. 최상위 요청 객체
data class ExternalWorkoutApiRequest(
    val input: WorkoutApiInput,
    val config: WorkoutApiConfig,
    val kwargs: Map<String, Any> = emptyMap()
)

// LangServe /invoke 응답은 최종 그래프 상태를 output 필드에 담아 반환한다.
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalWorkoutApiResponse(
    val output: WorkoutApiInput
)

// 2. 'input' 필드에 해당하는 객체이자 LangGraph가 반환하는 상태 객체
data class WorkoutApiInput(
    val day: Int,
    val phase: Int,
    val workouts_in_week: Int,
    val workout_length: String,
    val extra_criteria: String,
    val current_workout: Map<String, Any>,
    val created_workouts: List<Map<String, Any>>,
    val client_info: String,
    val user_feedback: String,
    val done: Boolean,
    val thread_id: String
)

// 3. 'config' 필드에 해당하는 객체
data class WorkoutApiConfig(
    val configurable: Configurable
)

// 4. 'configurable' 필드에 해당하는 객체
data class Configurable(
    val thread_id: String,
    val thread_ts: String = "" // API 명세상 비어있을 수 있으므로 기본값 설정
)
