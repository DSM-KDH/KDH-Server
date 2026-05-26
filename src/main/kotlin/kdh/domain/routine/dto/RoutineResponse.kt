package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.WorkoutSection
import java.time.LocalDate

@Schema(description = "날짜별 루틴 상세 조회 응답")
data class RoutineDateResponse(
    @field:Schema(description = "조회한 날짜", example = "2026-04-29")
    val date: LocalDate,

    @field:Schema(description = "해당 날짜의 운동 목록입니다. 운동이 없으면 빈 배열입니다.")
    val workouts: List<RoutineWorkoutItemResponse>
)

@Schema(description = "운동 항목 응답")
data class RoutineWorkoutItemResponse(
    @field:Schema(description = "운동 ID. 운동 완료 상태 변경 API에서 사용합니다.", example = "1")
    val exerciseId: Long,

    @field:Schema(description = "운동 섹션명", example = "Warm up")
    val sectionName: String,

    @field:Schema(description = "운동명", example = "자전거 운동")
    val exerciseName: String,

    @field:Schema(description = "반복 횟수 또는 운동 시간", example = "5분")
    val repsTime: String?,

    @field:Schema(description = "운동 완료 여부", example = "false")
    val completed: Boolean
) {
    companion object {
        fun from(section: WorkoutSection, exercise: ExerciseDetail): RoutineWorkoutItemResponse {
            return RoutineWorkoutItemResponse(
                exerciseId = exercise.id,
                sectionName = section.normalizedName(),
                exerciseName = exercise.exerciseName,
                repsTime = exercise.repsTime,
                completed = exercise.completed
            )
        }
    }
}

@Schema(description = "운동 완료 상태 변경 응답")
data class ExerciseCompletionResponse(
    @field:Schema(description = "완료 상태가 변경된 운동 ID", example = "1")
    val exerciseId: Long,

    @field:Schema(description = "변경된 완료 여부", example = "true")
    val completed: Boolean
)

@Schema(description = "삭제된 미래 루틴 요약 정보")
data class RoutineDeleteResponse(
    @field:Schema(description = "삭제된 루틴 개수", example = "1")
    val deletedRoutineCount: Int,

    @field:Schema(description = "삭제된 일자별 운동(일수) 개수", example = "28")
    val deletedDailyWorkoutCount: Int,

    @field:Schema(description = "삭제된 운동 섹션 개수", example = "28")
    val deletedWorkoutSectionCount: Int,

    @field:Schema(description = "삭제된 개별 운동 상세 개수", example = "224")
    val deletedExerciseCount: Int
)

@Schema(description = "지난주 루틴 달성률 응답")
data class RoutineAchievementRateResponse(
    @field:Schema(description = "지난주 시작일", example = "2026-04-27")
    val startDate: LocalDate,

    @field:Schema(description = "지난주 종료일", example = "2026-05-03")
    val endDate: LocalDate,

    @field:Schema(description = "기간 내 총 운동 개수", example = "20")
    val totalExerciseCount: Long,

    @field:Schema(description = "기간 내 완료한 운동 개수", example = "15")
    val completedExerciseCount: Long,

    @field:Schema(description = "달성률 (%)", example = "75.0")
    val achievementRate: Double
)

private fun WorkoutSection.normalizedName(): String {
    val cleanName = name.trim().replace(Regex("^\\d+\\s*[.)-]\\s*"), "")
    return when {
        cleanName.contains("warm", ignoreCase = true) -> "Warm up"
        cleanName.contains("balance", ignoreCase = true) || cleanName.contains("core", ignoreCase = true) -> "Balance"
        cleanName.contains("strength", ignoreCase = true) -> "Strength"
        cleanName.contains("cooldown", ignoreCase = true) || cleanName.contains("cool down", ignoreCase = true) -> "Cooldown"
        else -> cleanName
    }
}

@Schema(description = "주차별 루틴 달성률 응답")
data class WeeklyAchievementRateResponse(
    @field:Schema(description = "주차 번호", example = "1")
    val weekNumber: Int,

    @field:Schema(description = "주차 시작일", example = "2026-05-25")
    val startDate: LocalDate,

    @field:Schema(description = "주차 종료일", example = "2026-05-31")
    val endDate: LocalDate,

    @field:Schema(description = "해당 주차의 총 운동 개수", example = "9")
    val totalExerciseCount: Long,

    @field:Schema(description = "해당 주차의 완료한 운동 개수", example = "6")
    val completedExerciseCount: Long,

    @field:Schema(description = "달성률 (%)", example = "66.67")
    val achievementRate: Double,

    @field:Schema(description = "다음 루틴 시작까지 남은 일수", example = "5")
    val daysUntilNextRoutine: Long? = null
)
