package kdh.infra.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.IOException

@Service
class FcmService(
    @Value("\${fcm.key-file-path}") private val keyFilePath: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val serviceAccount = FileInputStream(keyFilePath)
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()
                FirebaseApp.initializeApp(options)
                log.info("Firebase Application has been initialized successfully using service account key: {}", keyFilePath)
            } else {
                log.info("Firebase Application already initialized.")
            }
        } catch (e: IOException) {
            log.error("Failed to initialize Firebase Application. JSON Key file not found or invalid: {}", keyFilePath, e)
        }
    }

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

        try {
            val message = Message.builder()
                .setToken(targetToken)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            log.info("FCM notification sent. title={}, messageId={}", title, response)
        } catch (e: Exception) {
            log.warn("FCM notification failed. title={}, reason={}", title, e.message, e)
        }
    }
}

