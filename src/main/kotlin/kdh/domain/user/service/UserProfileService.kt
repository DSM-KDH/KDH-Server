package kdh.domain.user.service

import kdh.domain.user.dto.UserProfileResponse
import kdh.domain.user.dto.UserProfileUpdateRequest
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import kdh.domain.user.exception.ProfileNotFoundException
import kdh.domain.user.exception.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserProfileService(
    private val userRepository: UserRepository,
    private val userProfileHistoryRepository: UserProfileHistoryRepository
) {

    @Transactional
    fun updateProfile(provider: String, providerId: String, request: UserProfileUpdateRequest): UserProfileResponse {
        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)

        val profile = userProfileHistoryRepository.save(
            UserProfileHistory(
                user = user,
                heightCm = request.heightCm,
                weightKg = request.weightKg,
                gender = request.gender
            )
        )

        return UserProfileResponse.from(profile)
    }

    @Transactional(readOnly = true)
    fun getLatestProfile(provider: String, providerId: String): UserProfileResponse {
        val profile = userProfileHistoryRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc(
            provider,
            providerId
        ) ?: throw ProfileNotFoundException()

        return UserProfileResponse.from(profile)
    }

    @Transactional(readOnly = true)
    fun getProfileHistory(provider: String, providerId: String): List<UserProfileResponse> {
        return userProfileHistoryRepository.findByUserProviderAndUserProviderIdOrderByRecordedAtDesc(provider, providerId)
            .map(UserProfileResponse::from)
    }

    @Transactional(readOnly = true)
    fun hasProfile(provider: String, providerId: String): Boolean {
        return userProfileHistoryRepository.existsByUserProviderAndUserProviderId(provider, providerId)
    }
}
