package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class InvalidRoutineGenerationResultException(
    message: String
) : KdhException(ErrorCode.INVALID_ROUTINE_RESULT, message)
