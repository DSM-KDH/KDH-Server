package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "선호 운동 유형. CARDIO=유산소, STRENGTH=근력, BODYWEIGHT=맨몸 운동")
enum class ExerciseType { CARDIO, STRENGTH, BODYWEIGHT }
