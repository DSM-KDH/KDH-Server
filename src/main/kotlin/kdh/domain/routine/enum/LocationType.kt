package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "운동 장소. OUTDOOR=야외, GYM=헬스장, HOME=집")
enum class LocationType { OUTDOOR, GYM, HOME }
