package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.RoutineCreateRequest
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class RoutineService(
    private val rabbitTemplate: RabbitTemplate
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    fun createRoutine(request: RoutineCreateRequest) {
        val message = objectMapper.writeValueAsString(request)
        rabbitTemplate.convertAndSend("routine.exchange", "routine.create.key", message)
    }
}
