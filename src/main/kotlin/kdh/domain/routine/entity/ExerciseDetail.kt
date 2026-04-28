package kdh.domain.routine.entity

import jakarta.persistence.*

@Entity
class ExerciseDetail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val exerciseName: String,

    val repsTime: String? = null,

    @Column(nullable = false)
    val isAlternative: Boolean = false,

    @Column(nullable = false)
    var completed: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    var section: WorkoutSection? = null
)
