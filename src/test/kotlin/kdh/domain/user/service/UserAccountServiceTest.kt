package kdh.domain.user.service

import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class UserAccountServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var profileRepository: UserProfileHistoryRepository
    private lateinit var routineRepository: RoutineRepository
    private lateinit var service: UserAccountService

    @BeforeEach
    fun setUp() {
        userRepository = Mockito.mock(UserRepository::class.java)
        profileRepository = Mockito.mock(UserProfileHistoryRepository::class.java)
        routineRepository = Mockito.mock(RoutineRepository::class.java)
        service = UserAccountService(userRepository, profileRepository, routineRepository)
    }

    @Test
    fun `withdraw deletes user owned data and account`() {
        val user = User(provider = "kakao", providerId = "user-1", name = "Tester")
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "user-1")).thenReturn(user)

        service.withdraw("kakao", "user-1")

        val inOrder = Mockito.inOrder(routineRepository, profileRepository, userRepository)
        inOrder.verify(routineRepository).deleteByUserProviderAndUserProviderId("kakao", "user-1")
        inOrder.verify(profileRepository).deleteByUserProviderAndUserProviderId("kakao", "user-1")
        inOrder.verify(userRepository).delete(user)
    }

    @Test
    fun `withdraw throws when user does not exist`() {
        Mockito.`when`(userRepository.findByProviderAndProviderId("kakao", "missing")).thenReturn(null)

        assertThatThrownBy { service.withdraw("kakao", "missing") }
            .isInstanceOf(IllegalArgumentException::class.java)

        Mockito.verify(routineRepository, Mockito.never()).deleteByUserProviderAndUserProviderId(Mockito.anyString(), Mockito.anyString())
        Mockito.verify(profileRepository, Mockito.never()).deleteByUserProviderAndUserProviderId(Mockito.anyString(), Mockito.anyString())
        Mockito.verify(userRepository, Mockito.never()).delete(Mockito.any())
    }
}
