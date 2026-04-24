package kdh.global.oauth

object OAuth2UserInfoFactory {
    fun getOAuth2UserInfo(provider: String, attributes: Map<String, Any>): OAuth2UserInfo {
        return when (provider.lowercase()) {
            "google" -> GoogleOAuth2UserInfo(attributes)
            else -> throw IllegalArgumentException("Invalid Provider Type.")
        }
    }
}
