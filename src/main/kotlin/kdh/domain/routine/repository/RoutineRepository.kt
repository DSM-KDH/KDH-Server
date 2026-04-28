package kdh.domain.routine.repository

import kdh.domain.routine.entity.Routine
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface RoutineRepository : JpaRepository<Routine, Long> {
    @EntityGraph(attributePaths = ["dailyWorkouts"])
    fun findDistinctByUserProviderAndUserProviderIdOrderByIdDesc(provider: String, providerId: String): List<Routine>

    fun findByIdAndUserProviderAndUserProviderId(id: Long, provider: String, providerId: String): Routine?
}
