package kdh.domain.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kdh.domain.user.dto.UserProfileResponse
import kdh.domain.user.dto.UserProfileUpdateRequest
import kdh.domain.user.service.UserProfileService
import kdh.global.oauth.CustomOAuth2User
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/me/profile")
@Tag(name = "USER_PROFILE", description = "사용자 신체 정보 API")
class UserProfileController(
    private val userProfileService: UserProfileService
) {

    @PostMapping
    @Operation(
        summary = "신체 정보 업데이트",
        description = "키, 몸무게, 성별을 저장합니다. 기존 row를 덮어쓰지 않고 매번 새 히스토리를 추가해 신체 변화 그래프에 사용할 수 있게 합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "신체 정보 저장 성공"),
            ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            ApiResponse(responseCode = "401", description = "JWT 인증 실패")
        ]
    )
    fun updateProfile(
        @Valid @RequestBody request: UserProfileUpdateRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<UserProfileResponse> {
        return ResponseEntity.ok(userProfileService.updateProfile(user.provider, user.providerId, request))
    }

    @GetMapping
    @Operation(
        summary = "최신 신체 정보 조회",
        description = "로그인 사용자의 가장 최근 신체 정보를 조회합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getLatestProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<UserProfileResponse> {
        return ResponseEntity.ok(userProfileService.getLatestProfile(user.provider, user.providerId))
    }

    @GetMapping("/history")
    @Operation(
        summary = "신체 정보 히스토리 조회",
        description = "월별/수시 업데이트된 신체 정보 전체 히스토리를 최신순으로 조회합니다. 그래프 데이터로 사용합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    fun getProfileHistory(
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<List<UserProfileResponse>> {
        return ResponseEntity.ok(userProfileService.getProfileHistory(user.provider, user.providerId))
    }
}
