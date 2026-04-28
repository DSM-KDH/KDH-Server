package kdh.domain.routine.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
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
@Tag(name = "ROUTINE", description = "AI 운동 루틴 생성, 조회, 완료 처리 API")
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
            생성된 운동은 날짜별로 저장되며, 날짜별 상세 조회 API로 확인할 수 있습니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun createRoutine(
        @Valid
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                루틴 생성 조건입니다.

                - `fcmToken`: 루틴 생성 완료 푸시 알림을 받을 FCM 토큰입니다.
                - `goal.goalType`: 운동 목표입니다. `DIET`는 체중 감량, `HEALTH_CARE`는 건강 관리, `MUSCLE_GAIN`은 근육 증가입니다.
                - `goal.targetWeight`: 목표 체중입니다. `goalType`이 `DIET`일 때 사용하며, 다른 목표에서는 `null`로 보낼 수 있습니다.
                - `goal.targetBodyParts`: 집중 발달할 신체 부위 목록입니다. `MUSCLE_GAIN`일 때 주로 사용합니다.
                - `fitnessLevel`: 현재 운동 수행 능력입니다. `BEGINNER`, `INTERMEDIATE`, `ADVANCED` 중 선택합니다.
                - `schedule.totalWeeks`: 루틴 기간입니다. 단위는 주이며 1~24 사이로 입력합니다.
                - `schedule.hoursPerDay`: 하루 운동 목표 시간입니다. 단위는 시간이며 1~5 사이로 입력합니다.
                - `schedule.activeDays`: 매주 운동할 요일 목록입니다. `MON`~`SUN` 중 선택하며, 목록 개수가 주간 운동 횟수가 됩니다.
                - `preferredExerciseTypes`: 선호 운동 유형 목록입니다. `CARDIO`, `STRENGTH`, `BODYWEIGHT` 중 하나 이상 선택합니다.
                - `environment.locations`: 운동 가능한 장소 목록입니다. `HOME`, `GYM`, `OUTDOOR` 중 하나 이상 선택합니다.
                - `environment.equipments`: 사용 가능한 운동 기구 목록입니다. 선택한 기구를 기반으로 운동을 추천합니다.
            """,
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RoutineCreateRequest::class),
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
        summary = "날짜별 루틴 상세 조회",
        description = """
            요청한 날짜에 있는 내 루틴 운동들을 자세히 조회합니다.

            `date`는 `yyyy-MM-dd` 형식으로 전달합니다.
            응답은 운동 하나하나가 리스트 형태로 내려가며 각 항목에는 운동 ID, 섹션명, 운동명, 반복/시간, 완료 여부가 포함됩니다.
            운동 완료 처리는 응답에 포함된 `exerciseId` 단위로 할 수 있습니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutineByDate(
        @Parameter(
            description = "상세 조회할 루틴 날짜입니다. 형식은 yyyy-MM-dd 입니다.",
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
            내가 가진 루틴 중 조회 가능한 날짜만 리스트로 조회합니다.

            서버 날짜 기준 최근 1개월 범위의 날짜만 반환합니다.
            DB에는 더 오래된 루틴이 남아 있어도 이 API에서는 반환하지 않습니다.
            상세 내용 없이 `yyyy-MM-dd` 날짜 배열만 반환합니다.
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
            운동 하나의 완료 여부를 변경합니다.

            완료 처리는 운동 ID 기준으로 하나씩 수행합니다.
            서버 날짜 기준 당일 운동만 완료 상태를 변경할 수 있습니다.
        """,
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun updateExerciseCompletion(
        @Parameter(description = "완료 상태를 변경할 운동 ID", example = "1", required = true)
        @PathVariable exerciseId: Long,
        @Parameter(description = "완료 여부입니다. true는 완료, false는 미완료입니다.", example = "true", required = true)
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
