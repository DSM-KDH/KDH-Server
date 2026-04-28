package kdh.domain.routine.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineDetailResponse
import kdh.domain.routine.dto.RoutineSummaryResponse
import kdh.domain.routine.service.RoutineService
import kdh.global.oauth.CustomOAuth2User
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/routines")
@Tag(name = "ROUTINE", description = "AI 운동 루틴 생성 API")
class RoutineController(
    private val routineService: RoutineService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @Operation(
        summary = "AI 루틴 생성 요청",
        description = "인증된 사용자의 루틴 생성 요청을 RabbitMQ 큐에 넣습니다. 실제 생성과 DB 저장은 비동기로 처리됩니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "루틴 생성 요청 접수"),
            ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = [Content()]),
            ApiResponse(responseCode = "401", description = "JWT 인증 실패", content = [Content()])
        ]
    )
    fun createRoutine(
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
                                "totalWeeks": 1,
                                "hoursPerDay": 1,
                                "activeDays": ["MON"]
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
        @Valid
        @org.springframework.web.bind.annotation.RequestBody
        request: RoutineCreateRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<Void> {
        log.info(
            "Routine creation request received. provider={}, providerId={}, totalWeeks={}, activeDays={}, goalType={}, fitnessLevel={}",
            principal.provider,
            principal.providerId,
            request.schedule.totalWeeks,
            request.schedule.activeDays,
            request.goal.goalType,
            request.fitnessLevel
        )
        routineService.createRoutine(request, principal.provider, principal.providerId)
        log.info("Routine creation request accepted. provider={}, providerId={}", principal.provider, principal.providerId)
        return ResponseEntity.ok().build()
    }

    @GetMapping
    @Operation(
        summary = "내 루틴 목록 조회",
        description = "로그인한 사용자에게 연결된 루틴 목록을 최신순으로 조회합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutines(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<List<RoutineSummaryResponse>> {
        return ResponseEntity.ok(routineService.getMyRoutines(principal.provider, principal.providerId))
    }

    @GetMapping("/dates")
    @Operation(
        summary = "내 루틴 날짜 목록 조회",
        description = "로그인한 사용자에게 연결된 모든 루틴 중 저번달 마지막 날까지의 운동 날짜만 리스트로 조회합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutineDates(
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<List<LocalDate>> {
        return ResponseEntity.ok(routineService.getMyRoutineDates(principal.provider, principal.providerId))
    }

    @GetMapping("/{routineId}")
    @Operation(
        summary = "내 루틴 상세 조회",
        description = "로그인한 사용자에게 연결된 특정 루틴의 일자, 섹션, 운동 목록을 조회합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutine(
        @PathVariable routineId: Long,
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<RoutineDetailResponse> {
        return ResponseEntity.ok(routineService.getMyRoutine(routineId, principal.provider, principal.providerId))
    }
}
