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

    @Test
    fun `withdraw deletes many owned routines and profiles without deleting another user data`() {
        val targetUser = entityManager.persist(User(provider = "kakao", providerId = "heavy-user", name = "Heavy"))
        val otherUser = entityManager.persist(User(provider = "kakao", providerId = "other-user", name = "Other"))
        repeat(12) { index ->
            entityManager.persist(
                UserProfileHistory(
                    user = targetUser,
                    heightCm = 170.0 + index,
                    weightKg = 70.0 + index,
                    gender = Gender.MALE
                )
            )
        }
        repeat(2) { index ->
            entityManager.persist(
                UserProfileHistory(
                    user = otherUser,
                    heightCm = 160.0 + index,
                    weightKg = 60.0 + index,
                    gender = Gender.FEMALE
                )
            )
        }
        repeat(3) { routineIndex ->
            persistRoutine(
                user = targetUser,
                startDate = LocalDate.of(2026, 5, 14).plusWeeks(routineIndex.toLong()),
                dailyWorkoutCount = 7,
                sectionsPerDay = 3,
                exercisesPerSection = 4
            )
        }
        persistRoutine(
            user = otherUser,
            startDate = LocalDate.of(2026, 6, 1),
            dailyWorkoutCount = 2,
            sectionsPerDay = 2,
            exercisesPerSection = 3
        )
        flushAndClear()

        service.withdraw("kakao", "heavy-user")
        flushAndClear()

        assertThat(userRepository.findByProviderAndProviderId("kakao", "heavy-user")).isNull()
        assertThat(userRepository.findByProviderAndProviderId("kakao", "other-user")).isNotNull
        assertThat(profileRepository.count()).isEqualTo(2L)
        assertThat(routineRepository.count()).isEqualTo(1L)
        assertThat(countEntities(DailyWorkout::class.java)).isEqualTo(2L)
        assertThat(countEntities(WorkoutSection::class.java)).isEqualTo(4L)
        assertThat(countEntities(ExerciseDetail::class.java)).isEqualTo(12L)
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

    private fun persistRoutine(
        user: User,
        startDate: LocalDate,
        dailyWorkoutCount: Int,
        sectionsPerDay: Int,
        exercisesPerSection: Int
    ) {
        val routine = Routine(user = user, totalWeeks = 1, startDate = startDate)
        repeat(dailyWorkoutCount) { dayIndex ->
            val dailyWorkout = DailyWorkout(day = dayIndex + 1, workoutDate = startDate.plusDays(dayIndex.toLong()))
            repeat(sectionsPerDay) { sectionIndex ->
                val section = WorkoutSection(name = "Section ${sectionIndex + 1}")
                repeat(exercisesPerSection) { exerciseIndex ->
                    section.addExercise(
                        ExerciseDetail(
                            exerciseName = "Exercise ${dayIndex + 1}-${sectionIndex + 1}-${exerciseIndex + 1}",
                            repsTime = "${exerciseIndex + 1}0 reps"
                        )
                    )
                }
                dailyWorkout.addSection(section)
            }
            routine.addDailyWorkout(dailyWorkout)
        }
        entityManager.persist(routine)
    }

    private fun countEntities(entityClass: Class<*>): Long {
        return entityManager.entityManager
            .createQuery("select count(e) from ${entityClass.simpleName} e", Long::class.javaObjectType)
            .singleResult
            .toLong()
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
