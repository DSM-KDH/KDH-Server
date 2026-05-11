package kdh.domain.routine.controller

import kdh.domain.routine.dto.EnvironmentSection
import kdh.domain.routine.dto.GoalSection
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.ScheduleSection
import kdh.domain.routine.enum.DayOfWeek
import kdh.domain.routine.enum.EquipmentType
import kdh.domain.routine.enum.ExerciseType
import kdh.domain.routine.enum.FitnessLevel
import kdh.domain.routine.enum.GoalType
import kdh.domain.routine.enum.LocationType

object RoutineTestFixtures {
    fun request(): RoutineCreateRequest {
        return RoutineCreateRequest(
            fcmToken = "fcm-token",
            goal = GoalSection(goalType = GoalType.HEALTH_CARE),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(totalWeeks = 4, hoursPerDay = 1, activeDays = listOf(DayOfWeek.MON)),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(
                locations = listOf(LocationType.HOME),
                equipments = listOf(EquipmentType.BAND)
            )
        )
    }
}
