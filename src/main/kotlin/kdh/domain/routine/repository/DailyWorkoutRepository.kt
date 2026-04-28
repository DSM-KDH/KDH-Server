package kdh.domain.routine.repository

import kdh.domain.routine.entity.DailyWorkout
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyWorkoutRepository : JpaRepository<DailyWorkout, Long> {
    @EntityGraph(attributePaths = ["sections"])
    fun findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate(
        provider: String,
        providerId: String,
        workoutDate: LocalDate
    ): List<DailyWorkout>
}
