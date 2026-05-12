package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class ExerciseNotFoundException(
    exerciseId: Long
) : KdhException(ErrorCode.EXERCISE_NOT_FOUND, "운동을 찾을 수 없습니다: $exerciseId")
