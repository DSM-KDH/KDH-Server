package kdh.domain.user.controller

import kdh.domain.user.service.UserAccountService
import kdh.global.oauth.CustomOAuth2User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus

class UserAccountControllerTest {

    private lateinit var userAccountService: UserAccountService
    private lateinit var controller: UserAccountController

    @BeforeEach
    fun setUp() {
        userAccountService = Mockito.mock(UserAccountService::class.java)
        controller = UserAccountController(userAccountService)
    }

    @Test
    fun `withdraw deletes authenticated account and returns no content`() {
        val response = controller.withdraw(principal())

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        Mockito.verify(userAccountService).withdraw("kakao", "user-1")
    }

    private fun principal(): CustomOAuth2User {
        return CustomOAuth2User(userName = "Tester", provider = "kakao", providerId = "user-1")
    }
}
