package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class WorkoutApiFailedException(
    retryCount: Int,
    cause: Throwable?
) : KdhException(
    ErrorCode.WORKOUT_API_FAILED,
    "Workout API 호출이 ${retryCount}회 모두 실패했습니다.",
    cause
)
