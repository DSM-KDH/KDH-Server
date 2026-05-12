package kdh.domain.routine.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class ProfileRequiredException : KdhException(ErrorCode.PROFILE_REQUIRED)
