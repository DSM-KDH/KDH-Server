package kdh.domain.routine.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kdh.domain.routine.dto.ExerciseCompletionResponse
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineDateResponse
import kdh.domain.routine.service.RoutineService
import kdh.global.oauth.CustomOAuth2User
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/routines")
@Tag(name = "ROUTINE", description = "AI 운동 루틴 생성, 날짜별 조회, 운동 완료 처리 API")
class RoutineController(
    private val routineService: RoutineService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @Operation(
        summary = "AI 루틴 생성 요청",
        description = """
            인증된 사용자의 조건을 바탕으로 AI 운동 루틴 생성을 요청합니다.

            요청은 RabbitMQ 큐에 등록되고, 실제 AI 호출과 DB 저장은 백그라운드에서 처리됩니다.
            생성된 운동은 날짜별로 저장되며, 이후 `GET /routines?date=yyyy-MM-dd`로 특정 날짜 운동 목록을 조회할 수 있습니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun createRoutine(
        @Valid
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "루틴 생성 조건",
            required = true,
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "홈트 초급자 건강관리 루틴",
                            value = """
                            {
                              "fcmToken": "sample-fcm-token",
                              "goal": {
                                "goalType": "HEALTH_CARE",
                                "targetWeight": null,
                                "targetBodyParts": []
                              },
                              "fitnessLevel": "BEGINNER",
                              "schedule": {
                                "totalWeeks": 4,
                                "hoursPerDay": 1,
                                "activeDays": ["MON", "WED", "FRI"]
                              },
                              "preferredExerciseTypes": ["BODYWEIGHT", "STRENGTH"],
                              "environment": {
                                "locations": ["HOME"],
                                "equipments": ["BAND", "FOAM_ROLLER"]
                              }
                            }
                            """
                        )
                    ]
                )
            ]
        )
        @RequestBody
        request: RoutineCreateRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<Void> {
        log.info(
            "Routine creation request received. provider={}, providerId={}, totalWeeks={}, activeDays={}",
            principal.provider,
            principal.providerId,
            request.schedule.totalWeeks,
            request.schedule.activeDays
        )
        routineService.createRoutine(request, principal.provider, principal.providerId)
        return ResponseEntity.ok().build()
    }

    @GetMapping
    @Operation(
        summary = "날짜별 운동 목록 조회",
        description = """
            요청한 날짜에 등록된 내 운동 목록을 리스트 형태로 조회합니다.

            `date`는 `yyyy-MM-dd` 형식으로 전달합니다.
            응답에는 섹션명, 운동명, 반복/시간 정보, 완료 여부, 당일 완료 가능 여부가 포함됩니다.
            해당 날짜에 운동이 없으면 빈 배열이 반환됩니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutineByDate(
        @Parameter(
            description = "조회할 운동 날짜. 형식은 yyyy-MM-dd 입니다.",
            example = "2026-04-28",
            required = true
        )
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate,
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<RoutineDateResponse> {
        return ResponseEntity.ok(routineService.getMyRoutineByDate(date, principal.provider, principal.providerId))
    }

    @GetMapping("/dates")
    @Operation(
        summary = "내 루틴 날짜 목록 조회",
        description = """
            내가 가진 루틴 중 조회 가능한 운동 날짜만 목록으로 반환합니다.

            서버 날짜 기준 최근 1개월 범위의 날짜만 조회됩니다.
            DB에는 더 오래된 루틴이 남아 있어도 이 API에서는 반환하지 않습니다.
            상세 운동 정보는 포함하지 않고 `yyyy-MM-dd` 날짜 배열만 반환합니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutineDates(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<List<LocalDate>> {
        return ResponseEntity.ok(routineService.getMyRoutineDates(principal.provider, principal.providerId))
    }

    @PatchMapping("/exercises/{exerciseId}/completion")
    @Operation(
        summary = "운동 완료 상태 변경",
        description = """
            특정 운동의 완료 여부를 변경합니다.

            운동 완료 체크는 서버 날짜 기준 당일 운동만 가능합니다.
            오늘 운동이 아닌 경우 완료 상태를 변경할 수 없습니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun updateExerciseCompletion(
        @Parameter(description = "완료 상태를 변경할 운동 ID", example = "1", required = true)
        @PathVariable exerciseId: Long,
        @Parameter(description = "완료 여부. true면 완료, false면 미완료입니다.", example = "true", required = true)
        @RequestParam completed: Boolean,
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<ExerciseCompletionResponse> {
        return ResponseEntity.ok(
            routineService.updateExerciseCompletion(
                exerciseId,
                completed,
                principal.provider,
                principal.providerId
            )
        )
    }
}
