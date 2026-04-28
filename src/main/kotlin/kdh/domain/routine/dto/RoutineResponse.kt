package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema
import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection

@Schema(description = "루틴 목록 응답")
data class RoutineSummaryResponse(
    val id: Long,
    val totalWeeks: Int,
    val dailyWorkoutCount: Int
) {
    companion object {
        fun from(routine: Routine): RoutineSummaryResponse {
            return RoutineSummaryResponse(
                id = routine.id,
                totalWeeks = routine.totalWeeks,
                dailyWorkoutCount = routine.dailyWorkouts.size
            )
        }
    }
}

@Schema(description = "루틴 상세 응답")
data class RoutineDetailResponse(
    val id: Long,
    val totalWeeks: Int,
    val dailyWorkouts: List<DailyWorkoutResponse>
) {
    companion object {
        fun from(routine: Routine): RoutineDetailResponse {
            return RoutineDetailResponse(
                id = routine.id,
                totalWeeks = routine.totalWeeks,
                dailyWorkouts = routine.dailyWorkouts
                    .sortedBy { it.day }
                    .map(DailyWorkoutResponse::from)
            )
        }
    }
}

data class DailyWorkoutResponse(
    val id: Long,
    val day: Int,
    val sections: List<WorkoutSectionResponse>
) {
    companion object {
        fun from(dailyWorkout: DailyWorkout): DailyWorkoutResponse {
            return DailyWorkoutResponse(
                id = dailyWorkout.id,
                day = dailyWorkout.day,
                sections = dailyWorkout.sections.map(WorkoutSectionResponse::from)
            )
        }
    }
}

data class WorkoutSectionResponse(
    val id: Long,
    val name: String,
    val exercises: List<ExerciseDetailResponse>
) {
    companion object {
        fun from(section: WorkoutSection): WorkoutSectionResponse {
            return WorkoutSectionResponse(
                id = section.id,
                name = section.name,
                exercises = section.exercises.map(ExerciseDetailResponse::from)
            )
        }
    }
}

data class ExerciseDetailResponse(
    val id: Long,
    val exerciseName: String,
    val sets: String?,
    val reps: String?,
    val rest: String?,
    val description: String?,
    val isAlternative: Boolean
) {
    companion object {
        fun from(exercise: ExerciseDetail): ExerciseDetailResponse {
            return ExerciseDetailResponse(
                id = exercise.id,
                exerciseName = exercise.exerciseName,
                sets = exercise.sets,
                reps = exercise.reps,
                rest = exercise.rest,
                description = exercise.description,
                isAlternative = exercise.isAlternative
            )
        }
    }
}
