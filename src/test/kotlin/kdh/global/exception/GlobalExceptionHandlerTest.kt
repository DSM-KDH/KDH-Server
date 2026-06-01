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
        assertThat(response.body?.message).isEqualTo("잘못된 요청입니다.")
        assertThat(response.body?.reason).isEqualTo("ILLEGAL_ARGUMENT")
        assertThat(response.body?.description).isEqualTo("bad input")
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
        assertThat(response.body?.message).isEqualTo("입력값 검증에 실패했습니다.")
        assertThat(response.body?.reason).isEqualTo("name")
        assertThat(response.body?.description).isEqualTo("name is required")
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
        assertThat(response.body?.message).isEqualTo("요청 바디 형식이 올바르지 않습니다.")
        assertThat(response.body?.reason).isEqualTo("HTTP_MESSAGE_NOT_READABLE")
        assertThat(response.body?.description).contains("missing fcmToken")
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
        assertThat(response.body?.message).isEqualTo("요청 파라미터 형식이 올바르지 않습니다.")
        assertThat(response.body?.reason).isEqualTo("date")
        assertThat(response.body?.description).isEqualTo("invalid date format. Use yyyy-MM-dd.")
    }

    @Test
    fun `handleNoResourceFoundException returns not found`() {
        val exception = NoResourceFoundException(HttpMethod.POST, "oauth2/withdrawal")

        val response = handler.handleNoResourceFoundException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).isEqualTo("요청한 리소스를 찾을 수 없습니다.")
        assertThat(response.body?.reason).isEqualTo("RESOURCE_NOT_FOUND")
        assertThat(response.body?.description).isEqualTo("method=POST, path=oauth2/withdrawal")
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
