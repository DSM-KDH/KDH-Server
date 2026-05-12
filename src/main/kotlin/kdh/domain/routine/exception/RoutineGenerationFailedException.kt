package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class RoutineGenerationFailedException(
    cause: Throwable
) : KdhException(ErrorCode.ROUTINE_GENERATION_FAILED, cause = cause)
