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
    @Value("\${DB_NAME}") private val dbName: String
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
        // 인증된 사용자 정보 추출
        val customOAuth2User = authentication.principal as CustomOAuth2User

        // JWT Access Token 및 Refresh Token 생성
        val accessToken = jwtUtil.generateAccessToken(customOAuth2User.providerId, customOAuth2User.name)
        val refreshToken = jwtUtil.generateRefreshToken(customOAuth2User.providerId, customOAuth2User.name)

        // Redis에 Refresh Token 저장
        val key = "$dbName:${customOAuth2User.providerId}"
        redisTemplate.opsForValue().set(
            key,
            refreshToken,
            jwtUtil.getRefreshTokenValidity(),
            TimeUnit.MILLISECONDS
        )

        // 프론트엔드 리다이렉트 URL 구성 (토큰을 쿼리 파라미터로 전달)
        val targetUrl = UriComponentsBuilder.fromUriString("/oauth2/success")
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .toUriString()

        // 세션에 저장된 인증 관련 임시 데이터 제거
        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}
