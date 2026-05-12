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
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/me")
@Tag(name = "USER_ACCOUNT", description = "User account API")
class UserAccountController(
    private val userAccountService: UserAccountService
) {

    @GetMapping
    @Operation(
        summary = "Get account profile",
        description = "Returns the authenticated user's name and profile image.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Account profile lookup succeeded"),
            ApiResponse(responseCode = "401", description = "JWT authentication failed")
        ]
    )
    fun getAccountProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<UserAccountProfileResponse> {
        return ResponseEntity.ok(userAccountService.getAccountProfile(user.provider, user.providerId))
    }

    @DeleteMapping
    @Operation(
        summary = "Withdraw account",
        description = "Deletes the authenticated user's routines, profile history, and account.",
        security = [SecurityRequirement(name = "Bearer Authentication")]
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Account withdrawal succeeded"),
            ApiResponse(responseCode = "401", description = "JWT authentication failed")
        ]
    )
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal user: CustomOAuth2User
    ): ResponseEntity<Void> {
        userAccountService.withdraw(user.provider, user.providerId)
        return ResponseEntity.noContent().build()
    }
}
