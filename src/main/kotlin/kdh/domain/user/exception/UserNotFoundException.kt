package kdh.domain.user.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class UserNotFoundException(
    provider: String,
    providerId: String,
    message: String = "사용자를 찾을 수 없습니다: $provider/$providerId"
) : KdhException(ErrorCode.USER_NOT_FOUND, message)
