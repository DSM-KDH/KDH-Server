package kdh.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import java.time.LocalDateTime

@Schema(description = "사용자 신체 정보 업데이트 요청. 수정할 때마다 기존 데이터를 덮어쓰지 않고 새 히스토리 row를 추가한다.")
data class UserProfileUpdateRequest(
    @field:DecimalMin(value = "50.0", message = "키는 50cm 이상이어야 합니다.")
    @field:DecimalMax(value = "250.0", message = "키는 250cm 이하여야 합니다.")
    @field:Schema(description = "사용자 키. 단위는 cm", example = "168.5")
    val heightCm: Double,

    @field:DecimalMin(value = "20.0", message = "몸무게는 20kg 이상이어야 합니다.")
    @field:DecimalMax(value = "300.0", message = "몸무게는 300kg 이하여야 합니다.")
    @field:Schema(description = "사용자 몸무게. 단위는 kg", example = "62.3")
    val weightKg: Double,

    @field:Schema(description = "사용자 성별", example = "FEMALE")
    val gender: Gender
)

@Schema(description = "사용자 신체 정보 응답")
data class UserProfileResponse(
    val id: Long,
    val heightCm: Double,
    val weightKg: Double,
    val gender: Gender,
    val recordedAt: LocalDateTime,
    val nextReminderAt: LocalDateTime
) {
    companion object {
        fun from(profile: UserProfileHistory): UserProfileResponse {
            return UserProfileResponse(
                id = profile.id,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                gender = profile.gender,
                recordedAt = profile.recordedAt,
                nextReminderAt = profile.nextReminderAt
            )
        }
    }
}
