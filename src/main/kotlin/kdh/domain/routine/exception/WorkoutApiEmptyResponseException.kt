package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class WorkoutApiEmptyResponseException : KdhException(ErrorCode.WORKOUT_API_EMPTY_RESPONSE)
