package kdh.domain.routine.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import kdh.domain.routine.enum.*

/**
 * AI 루틴 생성 최종 요청 DTO
 */
data class RoutineCreateRequest(
    val fcmToken: String,                 // FCM 토큰 (필수)

    @field:Valid
    val goal: GoalSection,                // [Step 1] 목적

    @field:Valid
    val fitnessLevel: FitnessLevel,       // [Step 2] 수행 능력

    @field:Valid
    val schedule: ScheduleSection,        // [Step 3] 일정 및 시간

    @field:NotEmpty(message = "선호하는 운동 유형을 하나 이상 선택해주세요.")
    val preferredExerciseTypes: List<ExerciseType>, // [Step 4] 운동 유형

    @field:Valid
    val environment: EnvironmentSection   // [Step 5] 장소 및 기구
)

/* --- 세부 섹션 클래스 --- */

data class GoalSection(
    val goalType: GoalType,
    val targetWeight: Double? = null,      // 다이어트 시 필수
    val targetBodyParts: List<BodyPart>? = emptyList() // 근육 증가 시 필수
)

data class ScheduleSection(
    @field:Min(1) @field:Max(24)
    val totalWeeks: Int,     // 유지 기간 (주)

    @field:Min(1) @field:Max(5)
    val hoursPerDay: Int,    // 하루 운동 시간

    @field:NotEmpty
    val activeDays: List<DayOfWeek> // 운동 요일
)

data class EnvironmentSection(
    @field:NotEmpty
    val locations: List<LocationType>,  // 장소 (야외, 헬스장, 집)

    @field:NotEmpty
    val equipments: List<EquipmentType> // 사용 가능한 기구 리스트
)
