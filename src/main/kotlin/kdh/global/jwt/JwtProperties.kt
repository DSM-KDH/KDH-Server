package kdh.global.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 설정 정보를 담는 Properties 클래스
 * application.yml의 jwt 설정을 바인딩
 *
 * @property secret JWT 서명에 사용할 비밀키
 * @property accessTokenValidity Access Token 유효 시간 (밀리초)
 * @property refreshTokenValidity Refresh Token 유효 시간 (밀리초)
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenValidity: Long,
    val refreshTokenValidity: Long
)
