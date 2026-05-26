package kdh.infra.fcm

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class FcmService(
    webClientBuilder: WebClient.Builder,
    @Value("\${fcm.server-key:}") private val serverKey: String,
    @Value("\${fcm.api-url:https://fcm.googleapis.com/fcm/send}") private val apiUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = webClientBuilder.build()
    private val effectiveApiUrl = apiUrl.ifBlank { DEFAULT_API_URL }

    fun sendNotification(title: String, body: String) {
        log.info("FCM notification skipped because target token is missing. title={}, body={}", title, body)
    }

    fun sendNotification(
        token: String?,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val targetToken = token?.takeIf { it.isNotBlank() }
        if (targetToken == null) {
            log.info("FCM notification skipped because target token is missing. title={}", title)
            return
        }

        if (serverKey.isBlank()) {
            log.info(
                "FCM notification skipped because FCM_SERVER_KEY is blank. tokenPresent={}, title={}, data={}",
                true,
                title,
                data
            )
            return
        }

        val payload = mapOf(
            "to" to targetToken,
            "notification" to mapOf(
                "title" to title,
                "body" to body
            ),
            "data" to data
        )

        runCatching {
            webClient.post()
                .uri(effectiveApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "key=$serverKey")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        }.onSuccess { response ->
            log.info("FCM notification sent. title={}, response={}", title, response)
        }.onFailure { e ->
            log.warn("FCM notification failed. title={}, reason={}", title, e.message, e)
        }
    }

    companion object {
        private const val DEFAULT_API_URL = "https://fcm.googleapis.com/fcm/send"
    }
}
