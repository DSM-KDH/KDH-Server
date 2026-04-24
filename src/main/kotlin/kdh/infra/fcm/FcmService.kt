package kdh.infra.fcm

import org.springframework.stereotype.Service

@Service
class FcmService {

    fun sendNotification(title: String, body: String) {
        // Implement FCM sending logic here.
        // This will typically involve using the Firebase Admin SDK.
        println("Sending FCM notification: Title='$title', Body='$body'")
    }
}
