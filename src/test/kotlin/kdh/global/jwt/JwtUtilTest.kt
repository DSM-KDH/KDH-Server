package kdh.global.jwt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtUtilTest {

    @Test
    fun `generateAccessToken embeds provider providerId and name claims`() {
        val jwtUtil = JwtUtil(properties(accessValidity = 60_000L))

        val token = jwtUtil.generateAccessToken("kakao", "user-1", "Tester")

        assertThat(jwtUtil.validateToken(token)).isTrue()
        assertThat(jwtUtil.getProvider(token)).isEqualTo("kakao")
        assertThat(jwtUtil.getProviderId(token)).isEqualTo("user-1")
        assertThat(jwtUtil.getName(token)).isEqualTo("Tester")
    }

    @Test
    fun `generateRefreshToken uses refresh validity and exposes configured validity`() {
        val jwtUtil = JwtUtil(properties(refreshValidity = 120_000L))

        val token = jwtUtil.generateRefreshToken("google", "user-2", "Refresh Tester")

        assertThat(jwtUtil.validateToken(token)).isTrue()
        assertThat(jwtUtil.getRefreshTokenValidity()).isEqualTo(120_000L)
        assertThat(jwtUtil.getProvider(token)).isEqualTo("google")
    }

    @Test
    fun `validateToken returns false for invalid or expired token`() {
        val jwtUtil = JwtUtil(properties(accessValidity = -1_000L))
        val expiredToken = jwtUtil.generateAccessToken("kakao", "user-1", "Tester")

        assertThat(jwtUtil.validateToken("not-a-jwt")).isFalse()
        assertThat(jwtUtil.validateToken(expiredToken)).isFalse()
    }

    private fun properties(
        accessValidity: Long = 60_000L,
        refreshValidity: Long = 600_000L
    ): JwtProperties {
        return JwtProperties(
            secret = "01234567890123456789012345678901",
            accessTokenValidity = accessValidity,
            refreshTokenValidity = refreshValidity
        )
    }
}
