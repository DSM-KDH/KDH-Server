package kdh.domain.routine.dto

data class WorkoutApiConfig(
    val configurable: Configurable
)

data class Configurable(
    val thread_id: String,
    val thread_ts: String = ""
)
