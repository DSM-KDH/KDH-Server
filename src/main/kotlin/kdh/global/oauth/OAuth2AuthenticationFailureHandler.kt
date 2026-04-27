package kdh.global.oauth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * OAuth2 인증 실패 처리 핸들러
 * OAuth2 로그인 실패 시 실행되는 핸들러로, 에러 메시지와 함께 실패 페이지로 리다이렉트
 */
@Component
class OAuth2AuthenticationFailureHandler(
    @Value("\${app.oauth.failure-redirect-uri}") private val failureRedirectUri: String
) : SimpleUrlAuthenticationFailureHandler() {

    /**
     * 인증 실패 시 호출되는 메서드
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param exception 발생한 인증 예외
     */
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val targetUrl = UriComponentsBuilder.fromUriString(failureRedirectUri)
            .queryParam("error", exception.localizedMessage)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}
