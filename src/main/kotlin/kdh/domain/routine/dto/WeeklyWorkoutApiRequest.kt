package kdh.domain.routine.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class WeeklyWorkoutApiRequest(
    val input: WeeklyWorkoutApiInput,
    val config: WorkoutApiConfig,
    val kwargs: Map<String, Any> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyWorkoutApiResponse(
    val output: WeeklyWorkoutApiOutput
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyWorkoutApiOutput(
    val weekly_routine: WeeklyRoutine,
    val done: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyRoutine(
    val title: String = "",
    val available_days_per_week: Int = 0,
    val days: List<WeeklyRoutineDay> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeeklyRoutineDay(
    val day: Int = 0,
    val title: String = "",
    val exercises: List<Map<String, Any>> = emptyList()
)

data class WeeklyWorkoutApiInput(
    val user_id: String,
    val available_days_per_week: Int,
    val daily_available_time: Int,
    val total_weeks: Int,
    val active_days: List<String>,
    val fitness_level: String,
    val goal_type: String,
    val target_weight: Double? = null,
    val target_body_parts: List<String> = emptyList(),
    val preferred_exercise_types: List<String>,
    val locations: List<String>,
    val equipments: List<String>,
    val extra_criteria: String,
    val equipment_limitations: String,
    val goals: String,
    val height: String = "",
    val weight: String = "",
    val gender: String = "",
    val client_info: String = ""
)
