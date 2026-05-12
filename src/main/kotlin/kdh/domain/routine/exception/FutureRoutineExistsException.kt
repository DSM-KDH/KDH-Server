package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException
import java.time.LocalDate

class FutureRoutineExistsException(
    firstFutureWorkoutDate: LocalDate
) : KdhException(
    ErrorCode.FUTURE_ROUTINE_EXISTS,
    "현재일 이후에 예정된 루틴이 있어 새 루틴을 생성할 수 없습니다. 가장 가까운 예정일: $firstFutureWorkoutDate"
)
