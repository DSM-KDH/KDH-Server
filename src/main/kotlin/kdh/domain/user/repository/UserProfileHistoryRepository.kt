package kdh.domain.user.repository

import kdh.domain.user.entity.UserProfileHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface UserProfileHistoryRepository : JpaRepository<UserProfileHistory, Long> {
    fun existsByUserProviderAndUserProviderId(provider: String, providerId: String): Boolean

    fun findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc(
        provider: String,
        providerId: String
    ): UserProfileHistory?

    fun findByUserProviderAndUserProviderIdOrderByRecordedAtDesc(
        provider: String,
        providerId: String
    ): List<UserProfileHistory>

    fun findByNextReminderAtLessThanEqual(nextReminderAt: LocalDateTime): List<UserProfileHistory>
}
