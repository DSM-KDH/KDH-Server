package kdh.global.oauth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OAuth2UserInfoFactoryTest {

    @Test
    fun `getOAuth2UserInfo maps google attributes`() {
        val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
            "google",
            mapOf(
                "sub" to "google-id",
                "email" to "tester@example.com",
                "name" to "Tester",
                "picture" to "profile.png"
            )
        )

        assertThat(userInfo.getProvider()).isEqualTo("google")
        assertThat(userInfo.getProviderId()).isEqualTo("google-id")
        assertThat(userInfo.getEmail()).isEqualTo("tester@example.com")
        assertThat(userInfo.getName()).isEqualTo("Tester")
        assertThat(userInfo.getProfileImage()).isEqualTo("profile.png")
    }

    @Test
    fun `getOAuth2UserInfo rejects unsupported provider`() {
        assertThatThrownBy { OAuth2UserInfoFactory.getOAuth2UserInfo("kakao", emptyMap()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
