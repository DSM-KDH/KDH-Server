package kdh.domain.routine.repository

import kdh.domain.routine.entity.Routine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RoutineRepository : JpaRepository<Routine, Long> {
    fun deleteByUserProviderAndUserProviderId(provider: String, providerId: String)

    @Modifying
    @Query(
        """
        delete from ExerciseDetail ed 
        where ed.section.id in (
            select ws.id from WorkoutSection ws 
            where ws.dailyWorkout.id in (
                select dw.id from DailyWorkout dw 
                where dw.routine.id in (
                    select r.id from Routine r 
                    where r.user.provider = :provider and r.user.providerId = :providerId
                )
            )
        )
        """
    )
    fun deleteExerciseDetailsByUser(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    )

    @Modifying
    @Query(
        """
        delete from WorkoutSection ws 
        where ws.dailyWorkout.id in (
            select dw.id from DailyWorkout dw 
            where dw.routine.id in (
                select r.id from Routine r 
                where r.user.provider = :provider and r.user.providerId = :providerId
            )
        )
        """
    )
    fun deleteWorkoutSectionsByUser(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    )

    @Modifying
    @Query(
        """
        delete from DailyWorkout dw 
        where dw.routine.id in (
            select r.id from Routine r 
            where r.user.provider = :provider and r.user.providerId = :providerId
        )
        """
    )
    fun deleteDailyWorkoutsByUser(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    )

    @Modifying
    @Query(
        """
        delete from Routine r 
        where r.user.provider = :provider and r.user.providerId = :providerId
        """
    )
    fun deleteRoutinesByUser(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    )

    @Query(
        """
        select distinct r
        from Routine r
        where r.user.provider = :provider
          and r.user.providerId = :providerId
          and exists (
            select 1
            from DailyWorkout futureDailyWorkout
            where futureDailyWorkout.routine = r
              and futureDailyWorkout.workoutDate > :today
          )
        """
    )
    fun findRoutinesWithFutureWorkouts(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String,
        @Param("today") today: LocalDate
    ): List<Routine>

    fun existsByUserProviderAndUserProviderId(provider: String, providerId: String): Boolean

    fun findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc(provider: String, providerId: String): Routine?

    @Query(
        """
        select r
        from Routine r
        where r.startDate <= :today
          and r.startDate > :cutoffDate
        """
    )
    fun findActiveRoutines(
        @Param("today") today: LocalDate,
        @Param("cutoffDate") cutoffDate: LocalDate
    ): List<Routine>
}
