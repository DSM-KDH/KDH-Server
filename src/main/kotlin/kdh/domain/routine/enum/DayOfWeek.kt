package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "운동 요일. MON=월, TUE=화, WED=수, THU=목, FRI=금, SAT=토, SUN=일")
enum class DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }
