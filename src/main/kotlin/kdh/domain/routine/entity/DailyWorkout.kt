package kdh.domain.routine.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
class DailyWorkout(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val day: Int, // 몇 일차 운동인지 (예: 1)

    val workoutDate: LocalDate? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    var routine: Routine? = null,

    @OneToMany(mappedBy = "dailyWorkout", cascade = [CascadeType.ALL], orphanRemoval = true)
    val sections: MutableList<WorkoutSection> = mutableListOf()
) {
    fun addSection(section: WorkoutSection) {
        sections.add(section)
        section.dailyWorkout = this
    }
}
