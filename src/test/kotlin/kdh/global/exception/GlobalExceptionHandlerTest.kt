package kdh.global.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleIllegalArgumentException returns bad request with exception message`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException("bad input"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("bad input")
    }

    @Test
    fun `handleException returns internal server error with exception message`() {
        val response = handler.handleException(RuntimeException("boom"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.message).isEqualTo("boom")
    }
}
