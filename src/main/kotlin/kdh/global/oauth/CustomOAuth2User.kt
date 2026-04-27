package kdh.global.oauth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * OAuth2 인증된 사용자 정보를 담는 커스텀 클래스
 * Spring Security의 OAuth2User를 구현하여 인증된 사용자의 세부 정보를 관리
 *
 * @property oauth2User OAuth2 제공자로부터 받은 원본 사용자 정보
 * @property userName 사용자 이름
 */
class CustomOAuth2User(
    private val oauth2User: OAuth2User? = null,
    val userName: String,
    val provider: String,
    val providerId: String,
) : OAuth2User {
    /**
     * 사용자 이름 반환
     */
    override fun getName(): String = userName

    /**
     * OAuth2 제공자로부터 받은 사용자 속성 반환
     */
    override fun getAttributes(): Map<String, Any>? = oauth2User?.attributes

    /**
     * 사용자 권한 목록 반환
     */
    override fun getAuthorities(): Collection<GrantedAuthority>? = oauth2User?.authorities
}