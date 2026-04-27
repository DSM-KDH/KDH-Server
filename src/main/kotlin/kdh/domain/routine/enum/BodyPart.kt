package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "집중 발달 부위. CHEST=가슴, BACK=등, SHOULDER=어깨, ARM=팔, ABS=복근, THIGH=허벅지, CALF=종아리, HIP=엉덩이")
enum class BodyPart { CHEST, BACK, SHOULDER, ARM, ABS, THIGH, CALF, HIP }
