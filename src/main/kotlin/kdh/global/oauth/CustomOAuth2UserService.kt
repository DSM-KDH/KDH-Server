package kdh.global.oauth

import kdh.domain.user.entity.User
import kdh.domain.user.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

/**
 * OAuth2 사용자 인증 서비스
 * OAuth2 제공자로부터 받은 사용자 정보를 처리하고 데이터베이스에 저장/업데이트
 *
 * @property userRepository 사용자 정보를 관리하는 Repository
 */
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository
) : DefaultOAuth2UserService() {

    /**
     * OAuth2 제공자로부터 사용자 정보를 로드하고 처리
     *
     * @param userRequest OAuth2 사용자 요청 정보
     * @return CustomOAuth2User 커스텀 OAuth2 사용자 객체
     */
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        // 부모 클래스에서 OAuth2 사용자 정보 로드
        val oauth2User = super.loadUser(userRequest)

        val userInfo = GoogleOAuth2UserInfo(oauth2User.attributes)

        // 사용자 정보 저장 또는 업데이트
        val user = saveOrUpdate(userInfo)

        // 커스텀 OAuth2User 객체 생성 및 반환
        return CustomOAuth2User(
            oauth2User = oauth2User,
            userName = user.name,
            providerId = userInfo.getProviderId()
        )
    }

    /**
     * 사용자 정보를 데이터베이스에 저장하거나 업데이트
     * 기존 사용자가 있으면 정보를 업데이트하고, 없으면 새로 생성
     *
     * @param userInfo OAuth2 제공자로부터 받은 사용자 정보
     * @return User 저장된 사용자 엔티티
     */
    private fun saveOrUpdate(userInfo: OAuth2UserInfo): User {
        val providerId = userInfo.getProviderId()

        // 제공자와 제공자 ID로 기존 사용자 조회
        val user = userRepository.findByProviderId(providerId)
            ?: User(
                    name = userInfo.getName(),
                    providerId = providerId,
                )
        return userRepository.save(user)
    }
}
