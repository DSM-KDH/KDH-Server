package kdh.domain.auth.controller

import kdh.domain.user.repository.UserRepository
import kdh.global.oauth.CustomOAuth2User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/oauth2")
@Tag(name = "AUTH", description = "OAuth2 콜백 API \n요청은 /oauth2/authorization/google")
class OAuth2Controller(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val userRepository: UserRepository,
    @Value("\${DB_NAME}") private val dbName: String
) {

    @GetMapping("/success")
    @Operation(
        summary = "OAuth2 로그인 성공 콜백",
        description = "OAuth2 로그인 성공 후 결과 정보를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = Map::class),
                        examples = [
                            ExampleObject(
                                value = "{\"success\":true,\"message\":\"로그인 성공\",\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJra2hoamoxMjA5QGdtYWlsLmNvbSIsInJvbGUiOiJ1c2VyIiwiaWF0IjoxNzczNzQzNzczLCJleHAiOjE3NzM3NDczNzN9.1Nw3nbFUHOwqVyZ2ft3RkO1zwxtN9Od3lbzDyfgYWPM\",\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJra2hoamoxMjA5QGdtYWlsLmNvbSIsInJvbGUiOiJ1c2VyIiwiaWF0IjoxNzczNzQzNzczLCJleHAiOjE3NzQzNDg1NzN9.5mRqEXB3-ZivNVFlPrmg6iUPLKNOOCgJ1fbhyC59g-s\"}"
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun loginSuccess(
        @Parameter(description = "백앤드 로직중 하나 프론트에서 건들거 없어요")
        @RequestParam accessToken: String,
        @Parameter(description = "백앤드 로직중 하나 프론트에서 건들거 없어요")
        @RequestParam refreshToken: String
    ): Map<String, Any> {
        return mapOf(
            "success" to true,
            "message" to "로그인 성공",
            "accessToken" to accessToken,
            "refreshToken" to refreshToken
        )
    }

    @GetMapping("/failure")
    @Operation(
        summary = "OAuth2 로그인 실패 콜백",
        description = "OAuth2 로그인 실패 시 오류 정보를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "로그인 실패",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = Map::class),
                        examples = [
                            ExampleObject(
                                value = "{\"success\":false,\"message\":\"로그인 실패\",\"error\":\"access_denied\"}"
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun loginFailure(
        @Parameter(description = "OAuth2 오류 사유", example = "access_denied")
        @RequestParam error: String
    ): Map<String, Any> {
        return mapOf(
            "success" to false,
            "message" to "로그인 실패",
            "error" to error
        )
    }

    @GetMapping("/login-test")
    @Operation(summary = "로그인 테스트", description = "로그인된 사용자의 정보를 반환합니다.")
    fun loginTest(@AuthenticationPrincipal user: CustomOAuth2User): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("provider" to user.provider, "providerId" to user.providerId, "name" to user.name))
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃을 처리하고 Refresh Token을 삭제합니다.")
    fun logout(@AuthenticationPrincipal user: CustomOAuth2User): ResponseEntity<Map<String, Any>> {
        val key = "$dbName:${user.provider}:${user.providerId}"
        redisTemplate.delete(key)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "로그아웃 되었습니다."))
    }

    @PostMapping("/withdrawal")
    @Transactional
    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴를 처리하고 사용자 정보와 Refresh Token을 삭제합니다.")
    fun withdrawal(@AuthenticationPrincipal user: CustomOAuth2User): ResponseEntity<Map<String, Any>> {
        userRepository.deleteByProviderAndProviderId(user.provider, user.providerId)
        val key = "$dbName:${user.provider}:${user.providerId}"
        redisTemplate.delete(key)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "회원 탈퇴 되었습니다."))
    }
}
