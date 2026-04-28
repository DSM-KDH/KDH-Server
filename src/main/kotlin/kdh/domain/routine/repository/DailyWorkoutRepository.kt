package kdh.domain.routine.repository

import kdh.domain.routine.entity.DailyWorkout
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyWorkoutRepository : JpaRepository<DailyWorkout, Long> {
    @EntityGraph(attributePaths = ["sections"])
    fun findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate(
        provider: String,
        providerId: String,
        workoutDate: LocalDate
    ): List<DailyWorkout>

    @Query(
        """
        select distinct d.workoutDate
        from DailyWorkout d
        where d.routine.user.provider = :provider
          and d.routine.user.providerId = :providerId
          and d.workoutDate between :fromDate and :toDate
        order by d.workoutDate
        """
    )
    fun findDistinctWorkoutDatesInRange(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String,
        @Param("fromDate") fromDate: LocalDate,
        @Param("toDate") toDate: LocalDate
    ): List<LocalDate>
}
