package kdh.domain.routine.entity

import jakarta.persistence.*
import kdh.domain.user.entity.User

@Entity
class Routine(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_provider_id", referencedColumnName = "provider_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val totalWeeks: Int,

    // Routine에 속한 모든 일자별 운동들
    @OneToMany(mappedBy = "routine", cascade = [CascadeType.ALL], orphanRemoval = true)
    val dailyWorkouts: MutableList<DailyWorkout> = mutableListOf()
) {
    fun addDailyWorkout(dailyWorkout: DailyWorkout) {
        dailyWorkouts.add(dailyWorkout)
        dailyWorkout.routine = this
    }
}
