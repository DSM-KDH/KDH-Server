package kdh.domain.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kdh.domain.user.dto.UserAccountProfileResponse
import kdh.domain.user.service.UserAccountService
import kdh.global.oauth.CustomOAuth2User
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/me")
@Tag(name = "USER_ACCOUNT", description = "내 계정 조회 및 회원탈퇴 API")
class UserAccountController(
    private val userAccountService: UserAccountService,
    private val redisTemplate: RedisTemplate<String, Any>,
    @Value("\${DB_NAME}") private val dbName: String
) {

    @GetMapping
    @Operation(
        summary = "내 계정 정보 조회",
        description = "로그인한 사용자의 이름과 프로필 이미지를 조회합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "내 계정 정보 조회 성공"),
            ApiResponse(responseCode = "401", description = "JWT 인증 실패")
        ]
    )
    fun getAccountProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<UserAccountProfileResponse> {
        return ResponseEntity.ok(userAccountService.getAccountProfile(user.provider, user.providerId))
    }

    @DeleteMapping
    @Operation(
        summary = "회원탈퇴",
        description = "로그인한 사용자의 루틴, 프로필 히스토리, 계정 정보, Refresh Token을 삭제합니다.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "회원탈퇴 성공"),
            ApiResponse(responseCode = "401", description = "JWT 인증 실패")
        ]
    )
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<Void> {
        userAccountService.withdraw(user.provider, user.providerId)
        redisTemplate.delete(refreshTokenKey(user))
        return ResponseEntity.noContent().build()
    }

    private fun refreshTokenKey(user: CustomOAuth2User): String {
        return "$dbName:${user.provider}:${user.providerId}"
    }
}
