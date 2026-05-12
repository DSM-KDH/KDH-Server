package kdh.domain.user.service

import kdh.domain.user.dto.UserProfileUpdateRequest
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import kdh.anyValue
import kdh.captureValue
import kdh.global.exception.KdhException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime

class UserProfileServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var profileRepository: UserProfileHistoryRepository
    private lateinit var service: UserProfileService

    @BeforeEach
    fun setUp() {
        userRepository = Mockito.mock(UserRepository::class.java)
        profileRepository = Mockito.mock(UserProfileHistoryRepository::class.java)
        service = UserProfileService(userRepository, profileRepository)
    }

    @Test
    fun `updateProfile saves a new history row and returns mapped response`() {
        val user = user()
        val request = UserProfileUpdateRequest(heightCm = 172.5, weightKg = 68.0, gender = Gender.MALE)
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user)
        Mockito.`when`(profileRepository.save(anyValue())).thenAnswer { invocation ->
            val profile = invocation.arguments[0] as UserProfileHistory
            UserProfileHistory(
                id = 10L,
                user = profile.user,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                gender = profile.gender,
                recordedAt = profile.recordedAt,
                nextReminderAt = profile.nextReminderAt
            )
        }

        val response = service.updateProfile("kakao", "user-1", request)

        val captor = ArgumentCaptor.forClass(UserProfileHistory::class.java)
        Mockito.verify(profileRepository).save(captureValue(captor))
        assertThat(captor.value.user).isSameAs(user)
        assertThat(captor.value.heightCm).isEqualTo(172.5)
        assertThat(captor.value.weightKg).isEqualTo(68.0)
        assertThat(captor.value.gender).isEqualTo(Gender.MALE)
        assertThat(response.id).isEqualTo(10L)
        assertThat(response.nextReminderAt).isEqualTo(response.recordedAt.plusMonths(1))
    }

    @Test
    fun `updateProfile throws when user does not exist`() {
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "missing")).thenReturn(null)

        assertThatThrownBy {
            service.updateProfile(
                "kakao",
                "missing",
                UserProfileUpdateRequest(heightCm = 170.0, weightKg = 65.0, gender = Gender.FEMALE)
            )
        }.isInstanceOf(KdhException::class.java)

        Mockito.verify(profileRepository, Mockito.never()).save(anyValue())
    }

    @Test
    fun `getLatestProfile returns latest profile or throws when absent`() {
        val recordedAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val profile = profile(id = 3L, recordedAt = recordedAt)
        Mockito.`when`(
            profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1")
        ).thenReturn(profile)
        Mockito.`when`(
            profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "empty")
        ).thenReturn(null)

        val response = service.getLatestProfile("kakao", "user-1")

        assertThat(response.id).isEqualTo(3L)
        assertThat(response.recordedAt).isEqualTo(recordedAt)
        assertThat(response.nextReminderAt).isEqualTo(recordedAt.plusMonths(1))
        assertThatThrownBy { service.getLatestProfile("kakao", "empty") }
            .isInstanceOf(KdhException::class.java)
    }

    @Test
    fun `getProfileHistory maps repository results in repository order`() {
        Mockito.`when`(
            profileRepository.findByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1")
        ).thenReturn(listOf(profile(id = 2L), profile(id = 1L)))

        val responses = service.getProfileHistory("kakao", "user-1")

        assertThat(responses.map { it.id }).containsExactly(2L, 1L)
    }

    @Test
    fun `hasProfile delegates existence check`() {
        Mockito.`when`(profileRepository.existsByUserProviderAndUserProviderId("kakao", "user-1")).thenReturn(true)

        assertThat(service.hasProfile("kakao", "user-1")).isTrue()
        Mockito.verify(profileRepository).existsByUserProviderAndUserProviderId("kakao", "user-1")
    }

    private fun user(): User {
        return User(
            provider = "kakao",
            providerId = "user-1",
            name = "Tester",
            profileImage = "profile.png"
        )
    }

    private fun profile(
        id: Long,
        recordedAt: LocalDateTime = LocalDateTime.of(2026, 5, 1, 10, 0)
    ): UserProfileHistory {
        return UserProfileHistory(
            id = id,
            user = user(),
            heightCm = 170.0,
            weightKg = 65.0,
            gender = Gender.FEMALE,
            recordedAt = recordedAt
        )
    }
}
