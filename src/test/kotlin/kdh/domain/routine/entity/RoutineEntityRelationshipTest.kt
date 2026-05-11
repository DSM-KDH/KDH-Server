package kdh.domain.routine.entity

import kdh.domain.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RoutineEntityRelationshipTest {

    @Test
    fun `adding daily workout sets both sides of routine relationship`() {
        val routine = Routine(user = user(), totalWeeks = 4)
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = LocalDate.now())

        routine.addDailyWorkout(dailyWorkout)

        assertThat(routine.dailyWorkouts).containsExactly(dailyWorkout)
        assertThat(dailyWorkout.routine).isSameAs(routine)
    }

    @Test
    fun `adding section and exercise sets parent references`() {
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = LocalDate.now())
        val section = WorkoutSection(name = "Strength")
        val exercise = ExerciseDetail(exerciseName = "Squat", repsTime = "10 reps")

        dailyWorkout.addSection(section)
        section.addExercise(exercise)

        assertThat(dailyWorkout.sections).containsExactly(section)
        assertThat(section.dailyWorkout).isSameAs(dailyWorkout)
        assertThat(section.exercises).containsExactly(exercise)
        assertThat(exercise.section).isSameAs(section)
    }

    private fun user(): User {
        return User(provider = "kakao", providerId = "user-1", name = "Tester")
    }
}
