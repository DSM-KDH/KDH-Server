package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "운동 목표. DIET=체중 감량, HEALTH_CARE=건강 관리, MUSCLE_GAIN=근육 증가")
enum class GoalType { DIET, HEALTH_CARE, MUSCLE_GAIN }
