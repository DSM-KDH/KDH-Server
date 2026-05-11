package kdh.domain.routine.repository

import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection
import kdh.domain.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.LocalDate

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:daily-workout-repository-test;NON_KEYWORDS=DAY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyWorkoutRepositoryTest @Autowired constructor(
    private val dailyWorkoutRepository: DailyWorkoutRepository,
    private val entityManager: TestEntityManager
) {

    @Test
    fun `findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate returns only owned workouts for date`() {
        val user = persistUser(providerId = "user-1")
        val otherUser = persistUser(providerId = "user-2")
        persistRoutine(user, LocalDate.of(2026, 5, 11), completed = false)
        persistRoutine(user, LocalDate.of(2026, 5, 12), completed = false)
        persistRoutine(otherUser, LocalDate.of(2026, 5, 11), completed = false)
        flushAndClear()

        val workouts = dailyWorkoutRepository.findByRoutineUserProviderAndRoutineUserProviderIdAndWorkoutDate(
            "kakao",
            "user-1",
            LocalDate.of(2026, 5, 11)
        )

        assertThat(workouts).hasSize(1)
        assertThat(workouts.first().workoutDate).isEqualTo(LocalDate.of(2026, 5, 11))
        assertThat(workouts.first().sections).hasSize(1)
    }

    @Test
    fun `findDistinctWorkoutDatesFrom returns sorted distinct dates from boundary`() {
        val user = persistUser()
        persistRoutine(user, LocalDate.of(2026, 4, 10), completed = false)
        persistRoutine(user, LocalDate.of(2026, 5, 1), completed = false)
        persistRoutine(user, LocalDate.of(2026, 5, 1), completed = true)
        persistRoutine(user, LocalDate.of(2026, 5, 3), completed = false)
        flushAndClear()

        val dates = dailyWorkoutRepository.findDistinctWorkoutDatesFrom(
            provider = "kakao",
            providerId = "user-1",
            fromDate = LocalDate.of(2026, 5, 1)
        )

        assertThat(dates).containsExactly(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3))
    }

    @Test
    fun `findFirstFutureWorkoutDate returns nearest workout after today`() {
        val user = persistUser()
        persistRoutine(user, LocalDate.of(2026, 5, 10), completed = false)
        persistRoutine(user, LocalDate.of(2026, 5, 12), completed = false)
        persistRoutine(user, LocalDate.of(2026, 5, 15), completed = false)
        flushAndClear()

        val date = dailyWorkoutRepository.findFirstFutureWorkoutDate(
            provider = "kakao",
            providerId = "user-1",
            today = LocalDate.of(2026, 5, 11)
        )

        assertThat(date).isEqualTo(LocalDate.of(2026, 5, 12))
    }

    @Test
    fun `exercise count queries count total and completed exercises in date range`() {
        val user = persistUser()
        persistRoutine(user, LocalDate.of(2026, 5, 5), completed = true)
        persistRoutine(user, LocalDate.of(2026, 5, 6), completed = false)
        persistRoutine(user, LocalDate.of(2026, 5, 20), completed = true)
        flushAndClear()

        val total = dailyWorkoutRepository.countExercisesBetweenDates(
            provider = "kakao",
            providerId = "user-1",
            startDate = LocalDate.of(2026, 5, 5),
            endDate = LocalDate.of(2026, 5, 11)
        )
        val completed = dailyWorkoutRepository.countCompletedExercisesBetweenDates(
            provider = "kakao",
            providerId = "user-1",
            startDate = LocalDate.of(2026, 5, 5),
            endDate = LocalDate.of(2026, 5, 11)
        )

        assertThat(total).isEqualTo(4L)
        assertThat(completed).isEqualTo(2L)
    }

    private fun persistRoutine(user: User, workoutDate: LocalDate, completed: Boolean): Routine {
        val routine = Routine(user = user, totalWeeks = 1, startDate = workoutDate)
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = workoutDate)
        val section = WorkoutSection(name = "Strength")
        section.addExercise(ExerciseDetail(exerciseName = "Squat", repsTime = "10 reps", completed = completed))
        section.addExercise(ExerciseDetail(exerciseName = "Push up", repsTime = "12 reps", completed = completed))
        dailyWorkout.addSection(section)
        routine.addDailyWorkout(dailyWorkout)
        entityManager.persist(routine)
        return routine
    }

    private fun persistUser(provider: String = "kakao", providerId: String = "user-1"): User {
        return entityManager.persist(User(provider = provider, providerId = providerId, name = "Tester"))
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
