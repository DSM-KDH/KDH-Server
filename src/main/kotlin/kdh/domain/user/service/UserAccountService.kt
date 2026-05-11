package kdh.domain.user.service

import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAccountService(
    private val userRepository: UserRepository,
    private val userProfileHistoryRepository: UserProfileHistoryRepository,
    private val routineRepository: RoutineRepository
) {

    @Transactional
    fun withdraw(provider: String, providerId: String) {
        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("User not found: $provider/$providerId")

        routineRepository.deleteByUserProviderAndUserProviderId(provider, providerId)
        userProfileHistoryRepository.deleteByUserProviderAndUserProviderId(provider, providerId)
        userRepository.delete(user)
    }
}
