package kdh.global.oauth.exception

import kdh.global.exception.ErrorCode
import kdh.global.exception.KdhException

class InvalidProviderTypeException : KdhException(ErrorCode.INVALID_PROVIDER_TYPE)
