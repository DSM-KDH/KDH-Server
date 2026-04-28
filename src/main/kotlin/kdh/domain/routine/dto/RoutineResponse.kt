package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.WorkoutSection
import java.time.LocalDate

@Schema(description = "Date-based routine response")
data class RoutineDateResponse(
    val date: LocalDate,
    val workouts: List<RoutineWorkoutItemResponse>
)

data class RoutineWorkoutItemResponse(
    val exerciseId: Long,
    val sectionName: String,
    val exerciseName: String,
    val repsTime: String?,
    val completed: Boolean,
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
    val exerciseId: Long,
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
