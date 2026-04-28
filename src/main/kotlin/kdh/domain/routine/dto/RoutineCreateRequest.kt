package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import kdh.domain.routine.enum.BodyPart
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType

@Schema(description = "AI 운동 루틴 생성 요청 본문입니다.")
data class RoutineCreateRequest(
    @field:Schema(
        description = "루틴 생성이 완료되었을 때 푸시 알림을 받을 FCM 토큰입니다.",
        example = "sample-fcm-token"
    )
    val fcmToken: String,

    @field:Valid
    @field:Schema(description = "운동 목표 정보입니다. 루틴의 방향성과 강도, 집중 부위를 결정하는 데 사용됩니다.")
    val goal: GoalSection,

    @field:Schema(
        description = "현재 운동 수행 능력입니다. BEGINNER는 초급자, INTERMEDIATE는 중급자, ADVANCED는 상급자를 의미합니다.",
        example = "BEGINNER"
    )
    val fitnessLevel: FitnessLevel,

    @field:Valid
    @field:Schema(description = "루틴 기간, 하루 운동 시간, 주간 운동 요일을 포함한 일정 정보입니다.")
    val schedule: ScheduleSection,

    @field:NotEmpty(message = "선호하는 운동 유형을 하나 이상 선택해주세요.")
    @field:ArraySchema(
        schema = Schema(
            description = "선호 운동 유형입니다. CARDIO, STRENGTH, BODYWEIGHT 중 하나 이상을 선택합니다.",
            example = "BODYWEIGHT"
        ),
        arraySchema = Schema(description = "사용자가 선호하는 운동 유형 목록입니다.")
    )
    val preferredExerciseTypes: List<ExerciseType>,

    @field:Valid
    @field:Schema(description = "운동 장소와 사용 가능한 운동 기구 정보입니다.")
    val environment: EnvironmentSection
)

@Schema(description = "운동 목표 정보입니다.")
data class GoalSection(
    @field:Schema(
        description = "운동 목표입니다. DIET는 체중 감량, HEALTH_CARE는 건강 관리, MUSCLE_GAIN은 근육 증가 목적입니다.",
        example = "HEALTH_CARE"
    )
    val goalType: GoalType,

    @field:Schema(
        description = "목표 체중입니다. goalType이 DIET일 때 체중 감량 목표로 사용합니다. 다른 목표에서는 null로 보낼 수 있습니다.",
        example = "65.0",
        nullable = true
    )
    val targetWeight: Double? = null,

    @field:ArraySchema(
        schema = Schema(
            description = "집중 발달할 신체 부위입니다. goalType이 MUSCLE_GAIN일 때 주로 사용합니다.",
            example = "CHEST"
        ),
        arraySchema = Schema(description = "근육 증가 루틴에서 집중할 신체 부위 목록입니다.")
    )
    val targetBodyParts: List<BodyPart>? = emptyList()
)

@Schema(description = "운동 일정 정보입니다.")
data class ScheduleSection(
    @field:Min(1)
    @field:Max(24)
    @field:Schema(
        description = "루틴을 진행할 기간입니다. 단위는 주입니다. 예를 들어 4이면 4주치 루틴을 생성합니다.",
        example = "4",
        minimum = "1",
        maximum = "24"
    )
    val totalWeeks: Int,

    @field:Min(1)
    @field:Max(5)
    @field:Schema(
        description = "하루 운동 목표 시간입니다. 단위는 시간입니다. 예를 들어 1이면 하루 1시간 루틴을 생성합니다.",
        example = "1",
        minimum = "1",
        maximum = "5"
    )
    val hoursPerDay: Int,

    @field:NotEmpty(message = "운동 요일을 하나 이상 선택해주세요.")
    @field:ArraySchema(
        schema = Schema(
            description = "운동할 요일입니다. MON, TUE, WED, THU, FRI, SAT, SUN 중 선택합니다.",
            example = "MON"
        ),
        arraySchema = Schema(description = "한 주에 운동할 요일 목록입니다. 목록 개수가 주간 운동 횟수로 사용됩니다.")
    )
    val activeDays: List<DayOfWeek>
)

@Schema(description = "운동 환경 정보입니다.")
data class EnvironmentSection(
    @field:NotEmpty(message = "운동 장소를 하나 이상 선택해주세요.")
    @field:ArraySchema(
        schema = Schema(
            description = "운동 장소입니다. HOME은 집, GYM은 헬스장, OUTDOOR는 야외 운동을 의미합니다.",
            example = "HOME"
        ),
        arraySchema = Schema(description = "운동 가능한 장소 목록입니다.")
    )
    val locations: List<LocationType>,

    @field:NotEmpty(message = "사용 가능한 운동 기구를 하나 이상 선택해주세요.")
    @field:ArraySchema(
        schema = Schema(
            description = "사용 가능한 운동 기구입니다. 선택한 기구를 기반으로 가능한 운동만 추천합니다.",
            example = "BAND"
        ),
        arraySchema = Schema(description = "사용 가능한 운동 기구 목록입니다.")
    )
    val equipments: List<EquipmentType>
)
