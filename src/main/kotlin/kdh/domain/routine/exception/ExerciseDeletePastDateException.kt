package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class ExerciseDeletePastDateException : KdhException(ErrorCode.EXERCISE_DELETE_PAST_DATE)
