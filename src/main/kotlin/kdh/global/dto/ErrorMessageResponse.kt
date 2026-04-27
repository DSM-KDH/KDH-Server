package kdh.global.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에러 응답")
data class ErrorMessageResponse(
    @field:Schema(description = "에러 메시지", example = "잘못된 요청입니다.")
    val message: String
)
