package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import kdh.domain.routine.enum.*

/**
 * AI 루틴 생성 최종 요청 DTO
 */
@Schema(
    description = "AI 루틴 생성 요청 전체 payload. 사용자의 목표, 운동 수준, 운동 가능 일정, 선호 운동, 운동 환경을 한 번에 받아 RabbitMQ로 넘기고, 백그라운드에서 Python 운동 생성 API를 호출해 DB에 저장한다."
)
data class RoutineCreateRequest(
    @field:Schema(description = "루틴 생성 완료 알림을 받을 FCM 토큰", example = "sample-fcm-token")
    val fcmToken: String,                 // FCM 토큰 (필수)

    @field:Valid
    @field:Schema(
        description = "운동 목표 묶음. AI가 루틴의 방향성을 정할 때 가장 먼저 보는 값이다. 감량, 건강관리, 근육 증가 중 하나를 선택하고, 목표별로 필요한 보조 값을 함께 넘긴다."
    )
    val goal: GoalSection,                // [Step 1] 목적

    @field:Valid
    @field:Schema(
        description = "사용자의 현재 운동 수행 능력. 같은 목표라도 초급자는 안정적이고 쉬운 동작 위주로, 중급/상급자는 난도와 운동량을 더 높여 생성하는 기준이 된다.",
        example = "BEGINNER"
    )
    val fitnessLevel: FitnessLevel,       // [Step 2] 수행 능력

    @field:Valid
    @field:Schema(
        description = "운동 일정 묶음. 총 몇 주 동안 진행할지, 하루 운동 시간은 어느 정도인지, 한 주에 어떤 요일 운동할지를 정한다. activeDays 개수가 Python API의 주간 운동 생성 개수로 들어간다."
    )
    val schedule: ScheduleSection,        // [Step 3] 일정 및 시간

    @field:NotEmpty(message = "선호하는 운동 유형을 하나 이상 선택해주세요.")
    @field:Schema(
        description = "사용자가 선호하는 운동 유형 목록. AI가 운동을 고를 때 cardio/strength/bodyweight 비중을 잡는 힌트로 사용한다. 하나 이상 필요하다.",
        example = "[\"BODYWEIGHT\", \"STRENGTH\"]"
    )
    val preferredExerciseTypes: List<ExerciseType>, // [Step 4] 운동 유형

    @field:Valid
    @field:Schema(
        description = "운동 환경 묶음. 사용자가 어디서 운동하는지와 어떤 기구를 쓸 수 있는지 전달한다. AI가 불가능한 기구나 장소 기반 운동을 추천하지 않도록 제한하는 역할이다."
    )
    val environment: EnvironmentSection   // [Step 5] 장소 및 기구
)

/* --- 세부 섹션 클래스 --- */

@Schema(
    description = "운동 목표 섹션. goalType이 핵심 값이고, targetWeight와 targetBodyParts는 목표에 따라 보조로 쓰인다. DIET이면 targetWeight가 감량 목표 설명에 반영되고, MUSCLE_GAIN이면 targetBodyParts가 집중 부위 설명에 반영된다."
)
data class GoalSection(
    @field:Schema(
        description = "운동 목표. DIET은 체중 감량, HEALTH_CARE는 전반적인 건강관리, MUSCLE_GAIN은 근육 증가 루틴으로 생성 방향이 달라진다.",
        example = "HEALTH_CARE"
    )
    val goalType: GoalType,

    @field:Schema(
        description = "목표 체중. goalType이 DIET일 때 감량 목표를 설명하는 데 사용한다. 다른 목표에서는 null이어도 된다.",
        example = "65.0",
        nullable = true
    )
    val targetWeight: Double? = null,      // 다이어트 시 필수

    @field:Schema(
        description = "집중 발달 부위 목록. goalType이 MUSCLE_GAIN일 때 어떤 신체 부위를 더 강조할지 AI 프롬프트에 반영된다. 건강관리/다이어트에서는 빈 배열이어도 된다.",
        example = "[\"CHEST\", \"BACK\"]"
    )
    val targetBodyParts: List<BodyPart>? = emptyList() // 근육 증가 시 필수
)

@Schema(
    description = "운동 일정 섹션. totalWeeks만큼 주차를 반복 생성하고, 각 주차마다 activeDays 개수만큼 일일 운동을 생성한다. hoursPerDay는 각 일일 운동의 목표 길이로 Python API에 전달된다."
)
data class ScheduleSection(
    @field:Min(1) @field:Max(24)
    @field:Schema(
        description = "루틴 유지 기간. 단위는 주. 예를 들어 4이면 4주치 루틴을 생성한다. 현재 phase는 주차 기준으로 1주차=1, 2~3주차=2, 4주차 이상=3으로 계산된다.",
        example = "1",
        minimum = "1",
        maximum = "24"
    )
    val totalWeeks: Int,     // 유지 기간 (주)

    @field:Min(1) @field:Max(5)
    @field:Schema(
        description = "하루 운동 시간. 단위는 시간. AI에는 '1시간' 같은 문자열로 전달되어 운동 개수와 세트/반복 수를 정하는 기준이 된다.",
        example = "1",
        minimum = "1",
        maximum = "5"
    )
    val hoursPerDay: Int,    // 하루 운동 시간

    @field:NotEmpty
    @field:Schema(
        description = "운동할 요일 목록. 실제 날짜 계산보다는 한 주에 몇 번 운동할지와 요일 선호를 나타낸다. 배열 길이가 주간 운동 생성 개수가 된다.",
        example = "[\"MON\", \"WED\", \"FRI\"]"
    )
    val activeDays: List<DayOfWeek> // 운동 요일
)

@Schema(
    description = "운동 환경 섹션. locations는 가능한 운동 장소, equipments는 사용 가능한 기구를 나타낸다. 둘 다 하나 이상 필요하며, AI가 장소/기구에 맞는 운동만 추천하도록 제한한다."
)
data class EnvironmentSection(
    @field:NotEmpty
    @field:Schema(
        description = "운동 장소 목록. HOME이면 홈트 위주, GYM이면 헬스장 기구 활용, OUTDOOR이면 야외 운동을 고려한다.",
        example = "[\"HOME\"]"
    )
    val locations: List<LocationType>,  // 장소 (야외, 헬스장, 집)

    @field:NotEmpty
    @field:Schema(
        description = "사용 가능한 기구 목록. 선택된 기구만 사용하도록 프롬프트에 들어간다. 홈트라면 BAND, FOAM_ROLLER처럼 실제 사용 가능한 기구만 넣는 것이 좋다.",
        example = "[\"BAND\", \"FOAM_ROLLER\"]"
    )
    val equipments: List<EquipmentType> // 사용 가능한 기구 리스트
)
