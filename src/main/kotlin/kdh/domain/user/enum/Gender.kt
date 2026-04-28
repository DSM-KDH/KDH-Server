package kdh.domain.user.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "성별. MALE=남성, FEMALE=여성")
enum class Gender {
    MALE,
    FEMALE
}
