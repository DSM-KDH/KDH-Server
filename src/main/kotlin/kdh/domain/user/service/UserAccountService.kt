package kdh.domain.user.service

import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.dto.UserAccountProfileResponse
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import kdh.domain.user.exception.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAccountService(
    private val userRepository: UserRepository,
    private val userProfileHistoryRepository: UserProfileHistoryRepository,
    private val routineRepository: RoutineRepository
) {

    @Transactional(readOnly = true)
    fun getAccountProfile(provider: String, providerId: String): UserAccountProfileResponse {
        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId, "User not found: $provider/$providerId")

        return UserAccountProfileResponse(
            name = user.name,
            profileImage = user.profileImage
        )
    }

    @Transactional
    fun withdraw(provider: String, providerId: String) {
        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId, "User not found: $provider/$providerId")

        routineRepository.deleteExerciseDetailsByUser(provider, providerId)
        routineRepository.deleteWorkoutSectionsByUser(provider, providerId)
        routineRepository.deleteDailyWorkoutsByUser(provider, providerId)
        routineRepository.deleteRoutinesByUser(provider, providerId)

        userProfileHistoryRepository.deleteByUserProviderAndUserProviderId(provider, providerId)
        userRepository.deleteByProviderAndProviderId(provider, providerId)
    }
}
