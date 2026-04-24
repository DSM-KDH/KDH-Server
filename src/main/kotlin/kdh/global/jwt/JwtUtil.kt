package kdh.global.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 토큰 생성 및 검증을 담당하는 유틸리티 클래스
 *
 * @property jwtProperties JWT 설정 정보
 */
@Component
@EnableConfigurationProperties(JwtProperties::class)
class JwtUtil(
    private val jwtProperties: JwtProperties
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    fun generateAccessToken(provider: String, providerId: String, name: String): String {
        return generateToken(provider, providerId, name, jwtProperties.accessTokenValidity)
    }

    fun generateRefreshToken(provider: String, providerId: String, name: String): String {
        return generateToken(provider, providerId, name, jwtProperties.refreshTokenValidity)
    }

    private fun generateToken(provider: String, providerId: String, name: String, validityInMilliseconds: Long): String {
        val now = Date()
        val validity = Date(now.time + validityInMilliseconds)

        return Jwts.builder()
            .subject(providerId)
            .claim("provider", provider)
            .claim("name", name)
            .issuedAt(now)
            .expiration(validity)
            .signWith(secretKey)
            .compact()
    }

    fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun getProvider(token: String): String {
        return getClaims(token)["provider"] as String
    }

    fun getProviderId(token: String): String {
        return getClaims(token).subject
    }

    fun getName(token: String): String {
        return getClaims(token)["name"] as String
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    fun getRefreshTokenValidity(): Long {
        return jwtProperties.refreshTokenValidity
    }
}
