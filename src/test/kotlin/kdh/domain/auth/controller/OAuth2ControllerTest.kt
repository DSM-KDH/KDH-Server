package kdh.domain.auth.controller

import kdh.domain.user.service.UserAccountService
import kdh.global.oauth.CustomOAuth2User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus

class OAuth2ControllerTest {

    private lateinit var redisTemplate: RedisTemplate<String, Any>
    private lateinit var userAccountService: UserAccountService
    private lateinit var controller: OAuth2Controller

    @BeforeEach
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        val redisMock = Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        redisTemplate = redisMock
        userAccountService = Mockito.mock(UserAccountService::class.java)
        controller = OAuth2Controller(redisTemplate, userAccountService, "test-db")
    }

    @Test
    fun `loginSuccess returns tokens and success payload`() {
        val response = controller.loginSuccess("access", "refresh")

        assertThat(response).containsEntry("success", true)
        assertThat(response).containsEntry("message", "로그인 성공")
        assertThat(response).containsEntry("accessToken", "access")
        assertThat(response).containsEntry("refreshToken", "refresh")
    }

    @Test
    fun `loginFailure returns error payload`() {
        val response = controller.loginFailure("access_denied")

        assertThat(response).containsEntry("success", false)
        assertThat(response).containsEntry("error", "access_denied")
    }

    @Test
    fun `loginTest returns unauthorized when principal is missing and user details otherwise`() {
        val unauthorized = controller.loginTest(null)
        val authorized = controller.loginTest(principal())

        assertThat(unauthorized.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(authorized.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(authorized.body).containsEntry("provider", "kakao")
        assertThat(authorized.body).containsEntry("providerId", "user-1")
        assertThat(authorized.body).containsEntry("name", "Tester")
    }

    @Test
    fun `logout deletes refresh token for authenticated user`() {
        val response = controller.logout(principal())

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).containsEntry("success", true)
        Mockito.verify(redisTemplate).delete("test-db:kakao:user-1")
    }

    @Test
    fun `logout returns unauthorized when principal is missing`() {
        val response = controller.logout(null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        Mockito.verifyNoInteractions(redisTemplate)
    }

    @Test
    fun `withdrawal deletes user and refresh token for authenticated user`() {
        val response = controller.withdrawal(principal())

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        Mockito.verify(userAccountService).withdraw("kakao", "user-1")
        Mockito.verify(redisTemplate).delete("test-db:kakao:user-1")
    }

    @Test
    fun `withdrawal returns unauthorized when principal is missing`() {
        val response = controller.withdrawal(null)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        Mockito.verifyNoInteractions(userAccountService, redisTemplate)
    }

    private fun principal(): CustomOAuth2User {
        return CustomOAuth2User(userName = "Tester", provider = "kakao", providerId = "user-1")
    }
}
