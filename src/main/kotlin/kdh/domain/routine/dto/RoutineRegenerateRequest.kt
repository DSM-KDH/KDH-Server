package kdh.domain.routine.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "AI 운동 루틴 재생성 요청 본문입니다.")
data class RoutineRegenerateRequest(
    @field:Schema(
        description = "루틴 재생성 피드백 및 수정 방향 사항입니다.",
        example = "상체 위주로 다시 짜주세요.",
        nullable = true
    )
    val feedback: String? = null,

    @field:Schema(
        description = "루틴 생성이 완료되었을 때 푸시 알림을 받을 FCM 토큰입니다.",
        example = "sample-fcm-token",
        nullable = true
    )
    val fcmToken: String? = null
)
