package kdh.domain.routine.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
@Tag(name = "ROUTINE", description = "AI workout routine API")
class RoutineController(
    private val routineService: RoutineService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @Operation(
        summary = "Create routine",
        description = "Queues routine generation and stores workouts by date.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun createRoutine(
        @Valid
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
        summary = "Get routine by date",
        description = "Returns workouts for the requested date. Query parameter format: yyyy-MM-dd.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getMyRoutineByDate(
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate,
        @Parameter(hidden = true)
        @AuthenticationPrincipal principal: CustomOAuth2User
    ): ResponseEntity<RoutineDateResponse> {
        return ResponseEntity.ok(routineService.getMyRoutineByDate(date, principal.provider, principal.providerId))
    }

    @PatchMapping("/exercises/{exerciseId}/completion")
    @Operation(
        summary = "Update exercise completion",
        description = "Only workouts scheduled for today can be checked as completed.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun updateExerciseCompletion(
        @PathVariable exerciseId: Long,
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
