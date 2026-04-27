package kdh.domain.routine.repository

import kdh.domain.routine.entity.Routine
import org.springframework.data.jpa.repository.JpaRepository

interface RoutineRepository : JpaRepository<Routine, Long>
