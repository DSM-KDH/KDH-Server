package kdh.domain.routine.dto

data class RoutineCreationMessage(
    val provider: String,
    val providerId: String,
    val request: RoutineCreateRequest,
    val feedback: String? = null,
    val regenerationCount: Int = 0
)
