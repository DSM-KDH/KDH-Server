package kdh.domain.user.repository

import kdh.domain.user.entity.UserProfileHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Modifying
    @Query("delete from UserProfileHistory uph where uph.user.provider = :provider and uph.user.providerId = :providerId")
    fun deleteByUserProviderAndUserProviderId(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    )

    fun findByNextReminderAtLessThanEqual(nextReminderAt: LocalDateTime): List<UserProfileHistory>
}
