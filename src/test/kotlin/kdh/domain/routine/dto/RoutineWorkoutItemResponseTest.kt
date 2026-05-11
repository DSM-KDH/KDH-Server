package kdh.domain.routine.dto

import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.WorkoutSection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoutineWorkoutItemResponseTest {

    @Test
    fun `normalizes warm up section names and maps exercise fields`() {
        val section = WorkoutSection(name = "1. Warm-up block")
        val exercise = ExerciseDetail(
            id = 42L,
            exerciseName = "Jumping Jack",
            repsTime = "30 seconds",
            completed = true
        )

        val response = RoutineWorkoutItemResponse.from(section, exercise)

        assertThat(response.exerciseId).isEqualTo(42L)
        assertThat(response.sectionName).isEqualTo("Warm up")
        assertThat(response.exerciseName).isEqualTo("Jumping Jack")
        assertThat(response.repsTime).isEqualTo("30 seconds")
        assertThat(response.completed).isTrue()
    }

    @Test
    fun `normalizes known section names and strips numeric prefixes`() {
        val cases = mapOf(
            "2) balance and core" to "Balance",
            "3 - Strength portion" to "Strength",
            "4. Cool down stretch" to "Cooldown",
            "5. Mobility" to "Mobility"
        )

        cases.forEach { (sectionName, expectedName) ->
            val response = RoutineWorkoutItemResponse.from(
                WorkoutSection(name = sectionName),
                ExerciseDetail(id = 1L, exerciseName = "Exercise")
            )

            assertThat(response.sectionName).isEqualTo(expectedName)
        }
    }
}
