package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "운동 수행 능력입니다. BEGINNER=초급자, INTERMEDIATE=중급자, ADVANCED=상급자")
enum class FitnessLevel { BEGINNER, INTERMEDIATE, ADVANCED }
