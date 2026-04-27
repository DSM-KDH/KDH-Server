package kdh.domain.routine.entity

import jakarta.persistence.*

@Entity
class ExerciseDetail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val exerciseName: String,
    val sets: String?,
    val reps: String?,
    val rest: String?,
    val description: String?,

    @Column(nullable = false)
    val isAlternative: Boolean = false, // 대체 운동 여부

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    var section: WorkoutSection? = null
)
