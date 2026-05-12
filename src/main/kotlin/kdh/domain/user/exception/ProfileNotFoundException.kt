package kdh.domain.user.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class ProfileNotFoundException(
    message: String = "사용자 신체 정보가 없습니다. 먼저 신체 정보를 등록해주세요."
) : KdhException(ErrorCode.PROFILE_NOT_FOUND, message)
