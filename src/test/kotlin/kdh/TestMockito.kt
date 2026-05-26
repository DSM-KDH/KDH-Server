package kdh

import kdh.domain.routine.entity.Routine
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDate
import java.time.LocalDateTime

@Suppress("UNCHECKED_CAST")
inline fun <reified T> anyValue(): T {
    Mockito.any(T::class.java)
    return dummyValue()
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> captureValue(captor: ArgumentCaptor<T>): T {
    captor.capture()
    return dummyValue()
}

inline fun <reified T> eqValue(value: T): T {
    Mockito.eq(value)
    return value
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> dummyValue(): T {
    val value: Any? = when (T::class) {
        String::class -> ""
        Map::class -> emptyMap<String, String>()
        LocalDate::class -> LocalDate.MIN
        LocalDateTime::class -> LocalDateTime.MIN
        Routine::class -> Routine(user = User(provider = "mock", providerId = "mock", name = "mock"), totalWeeks = 1)
        UserProfileHistory::class -> UserProfileHistory(
            user = User(provider = "mock", providerId = "mock", name = "mock"),
            heightCm = 170.0,
            weightKg = 70.0,
            gender = Gender.MALE
        )
        else -> null
    }
    return value as T
}
