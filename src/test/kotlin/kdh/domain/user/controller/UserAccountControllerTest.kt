package kdh.domain.user.controller

import kdh.domain.user.dto.UserAccountProfileResponse
import kdh.domain.user.service.UserAccountService
import kdh.global.oauth.CustomOAuth2User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus

class UserAccountControllerTest {

    private lateinit var userAccountService: UserAccountService
    private lateinit var redisTemplate: RedisTemplate<String, Any>
    private lateinit var controller: UserAccountController

    @BeforeEach
    fun setUp() {
        userAccountService = Mockito.mock(UserAccountService::class.java)
        @Suppress("UNCHECKED_CAST")
        val redisMock = Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, Any>
        redisTemplate = redisMock
        controller = UserAccountController(userAccountService, redisTemplate, "test-db")
    }

    @Test
    fun `getAccountProfile returns authenticated account profile`() {
        val expected = UserAccountProfileResponse(name = "Tester", profileImage = "profile.png")
        Mockito.`when`(userAccountService.getAccountProfile("kakao", "user-1")).thenReturn(expected)

        val response = controller.getAccountProfile(principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `withdraw deletes authenticated account and returns no content`() {
        val response = controller.withdraw(principal())

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        Mockito.verify(userAccountService).withdraw("kakao", "user-1")
        Mockito.verify(redisTemplate).delete("test-db:kakao:user-1")
    }

    private fun principal(): CustomOAuth2User {
        return CustomOAuth2User(userName = "Tester", provider = "kakao", providerId = "user-1")
    }
}
