package kdh.domain.user.service

import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.LocalDate

@DataJpaTest(
    showSql = false,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:user-account-service-test;NON_KEYWORDS=DAY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserAccountService::class)
class UserAccountServiceIntegrationTest @Autowired constructor(
    private val service: UserAccountService,
    private val userRepository: UserRepository,
    private val profileRepository: UserProfileHistoryRepository,
    private val routineRepository: RoutineRepository,
    private val entityManager: TestEntityManager
) {

    @Test
    fun `withdraw deletes account and persisted owned data`() {
        val user = entityManager.persist(User(provider = "kakao", providerId = "user-1", name = "Tester"))
        entityManager.persist(
            UserProfileHistory(
                user = user,
                heightCm = 175.0,
                weightKg = 70.0,
                gender = Gender.MALE
            )
        )
        persistRoutine(user)
        flushAndClear()

        service.withdraw("kakao", "user-1")
        flushAndClear()

        assertThat(userRepository.findByProviderAndProviderId("kakao", "user-1")).isNull()
        assertThat(profileRepository.count()).isZero()
        assertThat(routineRepository.count()).isZero()
        assertThat(countEntities(DailyWorkout::class.java)).isZero()
        assertThat(countEntities(WorkoutSection::class.java)).isZero()
        assertThat(countEntities(ExerciseDetail::class.java)).isZero()
    }

    private fun persistRoutine(user: User) {
        val routine = Routine(user = user, totalWeeks = 1, startDate = LocalDate.of(2026, 5, 14))
        val dailyWorkout = DailyWorkout(day = 1, workoutDate = LocalDate.of(2026, 5, 14))
        val section = WorkoutSection(name = "Strength")
        section.addExercise(ExerciseDetail(exerciseName = "Squat", repsTime = "10 reps"))
        dailyWorkout.addSection(section)
        routine.addDailyWorkout(dailyWorkout)
        entityManager.persist(routine)
    }

    private fun countEntities(entityClass: Class<*>): Long {
        return entityManager.entityManager
            .createQuery("select count(e) from ${entityClass.simpleName} e", java.lang.Long::class.java)
            .singleResult
            .toLong()
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
