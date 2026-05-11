package kdh.domain.user.service

import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.infra.fcm.FcmService
import kdh.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class UserProfileReminderServiceTest {

    private lateinit var profileRepository: UserProfileHistoryRepository
    private lateinit var fcmService: FcmService
    private lateinit var service: UserProfileReminderService

    @BeforeEach
    fun setUp() {
        profileRepository = Mockito.mock(UserProfileHistoryRepository::class.java)
        fcmService = Mockito.mock(FcmService::class.java)
        service = UserProfileReminderService(profileRepository, fcmService)
    }

    @Test
    fun `sendProfileUpdateReminders sends notification only for latest due profile`() {
        val latestDueProfile = profile(id = 2L)
        val staleDueProfile = profile(id = 1L)
        Mockito.`when`(profileRepository.findByNextReminderAtLessThanEqual(anyValue()))
            .thenReturn(listOf(staleDueProfile, latestDueProfile))
        Mockito.`when`(
            profileRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc("kakao", "user-1")
        ).thenReturn(latestDueProfile)

        service.sendProfileUpdateReminders()

        Mockito.verify(fcmService, Mockito.times(1)).sendNotification(Mockito.anyString(), Mockito.anyString())
        assertThat(latestDueProfile.nextReminderAt).isAfter(LocalDateTime.now().plusDays(25))
        assertThat(staleDueProfile.nextReminderAt).isEqualTo(staleDueProfile.recordedAt.plusMonths(1))
    }

    private fun profile(id: Long): UserProfileHistory {
        return UserProfileHistory(
            id = id,
            user = User(provider = "kakao", providerId = "user-1", name = "Tester"),
            heightCm = 170.0,
            weightKg = 65.0,
            gender = Gender.FEMALE,
            recordedAt = LocalDateTime.now().minusMonths(1).minusDays(id)
        )
    }
}
