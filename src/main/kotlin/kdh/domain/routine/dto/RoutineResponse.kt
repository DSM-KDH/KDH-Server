package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.WorkoutSection
import java.time.LocalDate

@Schema(description = "날짜별 운동 목록 응답")
data class RoutineDateResponse(
    @field:Schema(description = "조회한 날짜", example = "2026-04-28")
    val date: LocalDate,

    @field:Schema(description = "해당 날짜의 운동 목록. 운동이 없으면 빈 배열입니다.")
    val workouts: List<RoutineWorkoutItemResponse>
)

data class RoutineWorkoutItemResponse(
    @field:Schema(description = "운동 ID. 완료 상태 변경 API에서 사용합니다.", example = "1")
    val exerciseId: Long,

    @field:Schema(description = "운동 섹션명", example = "Warm up")
    val sectionName: String,

    @field:Schema(description = "운동명", example = "자전거 운동")
    val exerciseName: String,

    @field:Schema(description = "반복 횟수 또는 운동 시간", example = "5분")
    val repsTime: String?,

    @field:Schema(description = "운동 완료 여부", example = "false")
    val completed: Boolean,

    @field:Schema(description = "현재 요청 시점에 완료 체크 가능한 운동인지 여부. 서버 날짜 기준 당일 운동만 true입니다.", example = "true")
    val canComplete: Boolean
) {
    companion object {
        fun from(section: WorkoutSection, exercise: ExerciseDetail, canComplete: Boolean): RoutineWorkoutItemResponse {
            return RoutineWorkoutItemResponse(
                exerciseId = exercise.id,
                sectionName = section.normalizedName(),
                exerciseName = exercise.exerciseName,
                repsTime = exercise.repsTime,
                completed = exercise.completed,
                canComplete = canComplete
            )
        }
    }
}

data class ExerciseCompletionResponse(
    @field:Schema(description = "완료 상태가 변경된 운동 ID", example = "1")
    val exerciseId: Long,

    @field:Schema(description = "변경 후 완료 여부", example = "true")
    val completed: Boolean
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
