package kdh.global.oauth

import kdh.global.jwt.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.TimeUnit

/**
 * OAuth2 인증 성공 처리 핸들러
 * OAuth2 로그인 성공 시 JWT 토큰을 발급하고 프론트엔드로 리다이렉트
 *
 * @property jwtUtil JWT 토큰 생성 유틸리티
 */
@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtUtil: JwtUtil,
    private val redisTemplate: RedisTemplate<String, Any>,
    @Value("\${DB_NAME}") private val dbName: String,
    @Value("\${app.oauth.success-redirect-uri}") private val successRedirectUri: String
) : SimpleUrlAuthenticationSuccessHandler() {

    /**
     * 인증 성공 시 호출되는 메서드
     * JWT Access Token과 Refresh Token을 발급하여 쿼리 파라미터로 전달
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param authentication 인증 정보 객체 (CustomOAuth2User 포함)
     */
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val customOAuth2User = authentication.principal as CustomOAuth2User

        val accessToken = jwtUtil.generateAccessToken(customOAuth2User.provider, customOAuth2User.providerId, customOAuth2User.name)
        val refreshToken = jwtUtil.generateRefreshToken(customOAuth2User.provider, customOAuth2User.providerId, customOAuth2User.name)

        val key = "$dbName:${customOAuth2User.provider}:${customOAuth2User.providerId}"
        redisTemplate.opsForValue().set(
            key,
            refreshToken,
            jwtUtil.getRefreshTokenValidity(),
            TimeUnit.MILLISECONDS
        )

        val targetUrl = UriComponentsBuilder.fromUriString(successRedirectUri)
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .toUriString()

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}
