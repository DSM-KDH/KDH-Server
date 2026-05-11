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
    showSql = false,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:exercise-detail-repository-test;NON_KEYWORDS=DAY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExerciseDetailRepositoryTest @Autowired constructor(
    private val exerciseDetailRepository: ExerciseDetailRepository,
    private val entityManager: TestEntityManager
) {

    @Test
    fun `findByIdAndOwner returns exercise only for matching provider identity`() {
        val exercise = persistExercise(providerId = "user-1")
        persistExercise(providerId = "user-2")
        flushAndClear()

        val found = exerciseDetailRepository
            .findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                exercise.id,
                "kakao",
                "user-1"
            )
        val notOwned = exerciseDetailRepository
            .findByIdAndSectionDailyWorkoutRoutineUserProviderAndSectionDailyWorkoutRoutineUserProviderId(
                exercise.id,
                "kakao",
                "user-2"
            )

        assertThat(found).isNotNull
        assertThat(found?.exerciseName).isEqualTo("Squat")
        assertThat(found?.section?.dailyWorkout?.workoutDate).isEqualTo(LocalDate.of(2026, 5, 11))
        assertThat(notOwned).isNull()
    }

    private fun persistExercise(providerId: String): ExerciseDetail {
        val user = entityManager.persist(User(provider = "kakao", providerId = providerId, name = "Tester"))
        val routine = Routine(user = user, totalWeeks = 1, startDate = LocalDate.of(2026, 5, 11))
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = LocalDate.of(2026, 5, 11))
        val section = WorkoutSection(name = "Strength")
        val exercise = ExerciseDetail(exerciseName = "Squat", repsTime = "10 reps")
        section.addExercise(exercise)
        dailyWorkout.addSection(section)
        routine.addDailyWorkout(dailyWorkout)
        entityManager.persist(routine)
        return exercise
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
