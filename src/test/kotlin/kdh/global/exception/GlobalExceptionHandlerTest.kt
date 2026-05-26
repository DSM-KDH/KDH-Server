package kdh.global.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.HttpMethod
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleIllegalArgumentException returns bad request with exception message`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException("bad input"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("bad input")
    }

    @Test
    fun `handleMethodArgumentNotValidException returns bad request with validation message`() {
        val target = TestRequest(name = "")
        val bindingResult = BeanPropertyBindingResult(target, "testRequest")
        bindingResult.addError(FieldError("testRequest", "name", "name is required"))
        val parameter = MethodParameter(
            GlobalExceptionHandlerTest::class.java.getDeclaredMethod("handleTestRequest", TestRequest::class.java),
            0
        )
        val exception = MethodArgumentNotValidException(
            parameter,
            bindingResult
        )

        val response = handler.handleMethodArgumentNotValidException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("name is required")
    }

    @Test
    fun `handleHttpMessageNotReadableException returns bad request`() {
        val exception = HttpMessageNotReadableException(
            "missing body",
            RuntimeException("missing fcmToken"),
            MockHttpInputMessage(ByteArray(0))
        )

        val response = handler.handleHttpMessageNotReadableException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).contains("missing fcmToken")
    }

    @Test
    fun `handleMethodArgumentTypeMismatchException returns bad request for invalid date`() {
        val parameter = MethodParameter(
            GlobalExceptionHandlerTest::class.java.getDeclaredMethod("handleDateRequest", java.time.LocalDate::class.java),
            0
        )
        val exception = MethodArgumentTypeMismatchException(
            "2026-05-010",
            java.time.LocalDate::class.java,
            "date",
            parameter,
            IllegalArgumentException("bad date")
        )

        val response = handler.handleMethodArgumentTypeMismatchException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("invalid date format. Use yyyy-MM-dd.")
    }

    @Test
    fun `handleNoResourceFoundException returns not found`() {
        val exception = NoResourceFoundException(HttpMethod.POST, "oauth2/withdrawal")

        val response = handler.handleNoResourceFoundException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).isEqualTo("not found")
    }

    @Test
    fun `handleException returns internal server error with exception message`() {
        val response = handler.handleException(RuntimeException("boom"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.message).isEqualTo("boom")
    }

    private data class TestRequest(val name: String)

    @Suppress("unused")
    private fun handleTestRequest(request: TestRequest) = request

    @Suppress("unused")
    private fun handleDateRequest(date: java.time.LocalDate) = date
}
