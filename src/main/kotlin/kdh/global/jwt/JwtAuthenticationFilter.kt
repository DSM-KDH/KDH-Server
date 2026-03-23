package kdh.global.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kdh.global.oauth.CustomOAuth2User
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 인증 필터
 * 요청 헤더에서 JWT 토큰을 추출하고 검증하여 인증 정보를 SecurityContext에 설정
 *
 * @property jwtUtil JWT 유틸리티
 */
@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil
) : OncePerRequestFilter() {

    /**
     * 요청마다 실행되는 필터 메서드
     * JWT 토큰을 검증하고 인증 정보를 설정
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Authorization 헤더에서 JWT 토큰 추출
            val token = extractToken(request)

            // 토큰이 존재하고 유효한 경우
            if (token != null && jwtUtil.validateToken(token)) {
                // 토큰에서 사용자 정보 추출
                val providerId = jwtUtil.getProviderId(token)
                val name = jwtUtil.getName(token)

                // 인증 토큰 생성
                val authentication = UsernamePasswordAuthenticationToken(
                    CustomOAuth2User(
                        providerId = providerId,
                        userName = name
                    ),
                    null,
                    AuthorityUtils.NO_AUTHORITIES
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                // SecurityContext에 인증 정보 설정
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (e: Exception) {
            // 토큰 검증 실패 시 로깅 (선택적)
            logger.error("JWT 인증 실패: ${e.message}")
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response)
    }

    /**
     * Authorization 헤더에서 JWT 토큰 추출
     *
     * @param request HTTP 요청
     * @return JWT 토큰 (Bearer 제거), 없으면 null
     */
    private fun extractToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}
