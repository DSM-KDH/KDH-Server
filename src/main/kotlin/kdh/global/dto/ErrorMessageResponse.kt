package kdh.global.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에러 응답")
data class ErrorMessageResponse(
    @field:Schema(description = "에러 메시지", example = "잘못된 요청입니다.")
    val message: String,

    @field:Schema(description = "상세 사유 코드", example = "BMI_OUT_OF_RANGE", nullable = true)
    val reason: String? = null,

    @field:Schema(description = "상세 설명", example = "현재 BMI(15.00)가 루틴 생성이 불가능한 범위입니다.", nullable = true)
    val description: String? = null
)
