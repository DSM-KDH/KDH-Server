package kdh.global.oauth

import kdh.global.oauth.exception.InvalidProviderTypeException

object OAuth2UserInfoFactory {
    fun getOAuth2UserInfo(provider: String, attributes: Map<String, Any>): OAuth2UserInfo {
        return when (provider.lowercase()) {
            "google" -> GoogleOAuth2UserInfo(attributes)
            else -> throw InvalidProviderTypeException()
        }
    }
}
