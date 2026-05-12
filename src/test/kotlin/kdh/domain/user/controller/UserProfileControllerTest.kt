package kdh.domain.user.controller

import kdh.domain.user.dto.UserProfileResponse
import kdh.domain.user.dto.UserProfileUpdateRequest
import kdh.domain.user.enum.Gender
import kdh.domain.user.service.UserProfileService
import kdh.global.oauth.CustomOAuth2User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class UserProfileControllerTest {

    private lateinit var userProfileService: UserProfileService
    private lateinit var controller: UserProfileController

    @BeforeEach
    fun setUp() {
        userProfileService = Mockito.mock(UserProfileService::class.java)
        controller = UserProfileController(userProfileService)
    }

    @Test
    fun `updateProfile delegates authenticated owner and request`() {
        val request = UserProfileUpdateRequest(heightCm = 170.0, weightKg = 65.0, gender = Gender.FEMALE)
        val expected = response(id = 1L)
        Mockito.`when`(userProfileService.updateProfile("kakao", "user-1", request)).thenReturn(expected)

        val response = controller.updateProfile(request, principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `getLatestProfile returns latest profile`() {
        val expected = response(id = 2L)
        Mockito.`when`(userProfileService.getLatestProfile("kakao", "user-1")).thenReturn(expected)

        val response = controller.getLatestProfile(principal())

        assertThat(response.body).isSameAs(expected)
    }

    @Test
    fun `getProfileHistory returns profile history`() {
        val expected = listOf(response(id = 2L), response(id = 1L))
        Mockito.`when`(userProfileService.getProfileHistory("kakao", "user-1")).thenReturn(expected)

        val response = controller.getProfileHistory(principal())

        assertThat(response.body).isEqualTo(expected)
    }

    private fun response(id: Long): UserProfileResponse {
        val recordedAt = LocalDateTime.of(2026, 5, 11, 10, 0)
        return UserProfileResponse(
            id = id,
            heightCm = 170.0,
            weightKg = 65.0,
            gender = Gender.FEMALE,
            recordedAt = recordedAt,
            nextReminderAt = recordedAt.plusMonths(1)
        )
    }

    private fun principal(): CustomOAuth2User {
        return CustomOAuth2User(userName = "Tester", provider = "kakao", providerId = "user-1")
    }
}
