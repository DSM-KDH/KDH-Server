package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.RoutineCreateRequest
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class RoutineMessageHandler(
    private val routineGenerationService: RoutineGenerationService
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @RabbitListener(queues = ["routine.create.queue"])
    fun handleMessage(message: String) {
        val request = objectMapper.readValue(message, RoutineCreateRequest::class.java)
        routineGenerationService.generateMultiWeekRoutine(request)
    }
}
