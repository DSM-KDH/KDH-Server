package kdh.domain.routine.entity

import jakarta.persistence.*

@Entity
class WorkoutSection(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val name: String, // "Warm up", "Strength portion" 등

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_workout_id", nullable = false)
    var dailyWorkout: DailyWorkout? = null,

    @OneToMany(mappedBy = "section", cascade = [CascadeType.ALL], orphanRemoval = true)
    val exercises: MutableList<ExerciseDetail> = mutableListOf()
) {
    fun addExercise(exercise: ExerciseDetail) {
        exercises.add(exercise)
        exercise.section = this
    }
}
