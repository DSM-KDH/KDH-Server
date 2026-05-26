package kdh.global.exception

enum class ErrorCode(
    val status: Int,
    val message: String
) {
    USER_NOT_FOUND(400, "사용자를 찾을 수 없습니다."),
    PROFILE_NOT_FOUND(400, "사용자 신체 정보가 없습니다."),
    PROFILE_REQUIRED(400, "신체 정보가 없습니다. 키, 몸무게, 성별을 먼저 등록해야 루틴을 생성할 수 있습니다."),
    FUTURE_ROUTINE_EXISTS(400, "현재일 이후에 예정된 루틴이 있어 새 루틴을 생성할 수 없습니다."),
    EXERCISE_NOT_FOUND(400, "운동을 찾을 수 없습니다."),
    EXERCISE_WORKOUT_DATE_NOT_FOUND(400, "운동 날짜를 찾을 수 없습니다."),
    EXERCISE_COMPLETION_DATE_INVALID(400, "당일 운동만 완료 처리할 수 있습니다."),
    INVALID_PROVIDER_TYPE(400, "Invalid Provider Type."),
    WORKOUT_API_EMPTY_RESPONSE(502, "Workout API 응답이 없습니다."),
    WORKOUT_API_FAILED(502, "Workout API 호출에 실패했습니다."),
    ROUTINE_GENERATION_FAILED(500, "루틴 생성에 실패했습니다."),
    INVALID_DIET_CONDITION(400, "루틴 생성이 불가능한 조건이에요. 다시 작성해주세요."),
    REGENERATION_LIMIT_EXCEEDED(400, "루틴은 최대 1회만 재생성 가능합니다."),
    ROUTINE_NOT_FOUND(400, "조회 가능한 루틴이 없습니다."),
    INVALID_ROUTINE_RESULT(400, "생성된 루틴의 형식이 올바르지 않거나 한글이 지원되지 않습니다.")
}
