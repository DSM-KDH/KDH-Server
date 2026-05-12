package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class ExerciseWorkoutDateNotFoundException(
    exerciseId: Long
) : KdhException(ErrorCode.EXERCISE_WORKOUT_DATE_NOT_FOUND, "운동 날짜를 찾을 수 없습니다: $exerciseId")
