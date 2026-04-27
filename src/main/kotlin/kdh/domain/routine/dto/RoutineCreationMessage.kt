package kdh.domain.routine.dto

data class RoutineCreationMessage(
    val provider: String,
    val providerId: String,
    val request: RoutineCreateRequest
)
