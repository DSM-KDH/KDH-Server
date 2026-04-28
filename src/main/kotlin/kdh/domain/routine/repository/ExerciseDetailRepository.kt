package kdh.domain.routine.repository

import kdh.domain.routine.entity.ExerciseDetail
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface ExerciseDetailRepository : JpaRepository<ExerciseDetail, Long> {
    @EntityGraph(attributePaths = ["section", "section.dailyWorkout", "section.dailyWorkout.routine"])
    fun findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
        id: Long,
        provider: String,
        providerId: String
    ): ExerciseDetail?
}
