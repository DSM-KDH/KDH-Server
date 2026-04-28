package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema
import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection
import java.time.LocalDate

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
    val workoutDate: LocalDate?,
    val sections: List<WorkoutSectionResponse>
) {
    companion object {
        fun from(dailyWorkout: DailyWorkout): DailyWorkoutResponse {
            return DailyWorkoutResponse(
                id = dailyWorkout.id,
                day = dailyWorkout.day,
                workoutDate = dailyWorkout.workoutDate,
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
    val repsTime: String?,
    val isAlternative: Boolean,
    val completed: Boolean
) {
    companion object {
        fun from(exercise: ExerciseDetail): ExerciseDetailResponse {
            return ExerciseDetailResponse(
                id = exercise.id,
                exerciseName = exercise.exerciseName,
                repsTime = exercise.repsTime,
                isAlternative = exercise.isAlternative,
                completed = exercise.completed
            )
        }
    }
}
