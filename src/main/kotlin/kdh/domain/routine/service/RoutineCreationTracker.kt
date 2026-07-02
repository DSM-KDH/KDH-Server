package kdh.domain.routine.service

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class RoutineCreationTracker {
    private val activeCount = AtomicInteger(0)

    companion object {
        const val MAX_CONCURRENT_ROUTINES = 3
    }

    fun increment(): Int = activeCount.incrementAndGet()
    fun decrement(): Int = activeCount.decrementAndGet()
    fun getActiveCount(): Int = activeCount.get()
}
