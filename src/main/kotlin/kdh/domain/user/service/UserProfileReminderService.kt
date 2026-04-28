package kdh.domain.user.service

import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.infra.fcm.FcmService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserProfileReminderService(
    private val userProfileHistoryRepository: UserProfileHistoryRepository,
    private val fcmService: FcmService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    fun sendProfileUpdateReminders() {
        val now = LocalDateTime.now()
        val dueProfiles = userProfileHistoryRepository.findByNextReminderAtLessThanEqual(now)

        dueProfiles.forEach { profile ->
            val latestProfile = userProfileHistoryRepository.findFirstByUserProviderAndUserProviderIdOrderByRecordedAtDesc(
                profile.user.provider,
                profile.user.providerId
            )

            if (latestProfile?.id != profile.id) {
                return@forEach
            }

            log.info(
                "User profile update reminder due. provider={}, providerId={}, profileId={}, nextReminderAt={}",
                profile.user.provider,
                profile.user.providerId,
                profile.id,
                profile.nextReminderAt
            )
            fcmService.sendNotification(
                "신체 정보 업데이트 알림",
                "키, 몸무게, 성별 정보를 최신 상태로 업데이트해주세요."
            )
            profile.nextReminderAt = now.plusMonths(1)
        }
    }
}
