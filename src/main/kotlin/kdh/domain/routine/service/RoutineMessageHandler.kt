package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.RoutineCreationMessage
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RoutineMessageHandler(
    private val routineGenerationService: RoutineGenerationService
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = ["routine.create.queue"])
    fun handleMessage(message: String) {
        log.info("Routine creation message received. payloadBytes={}", message.toByteArray().size)
        val messagePayload = objectMapper.readValue(message, RoutineCreationMessage::class.java)
        try {
            log.info(
                "Routine generation started from queue. provider={}, providerId={}, totalWeeks={}, activeDays={}",
                messagePayload.provider,
                messagePayload.providerId,
                messagePayload.request.schedule.totalWeeks,
                messagePayload.request.schedule.activeDays
            )
            routineGenerationService.generateMultiWeekRoutine(
                messagePayload.request,
                messagePayload.provider,
                messagePayload.providerId
            )
            log.info(
                "Routine generation finished from queue. provider={}, providerId={}",
                messagePayload.provider,
                messagePayload.providerId
            )
        } catch (e: Exception) {
            log.error(
                "Routine generation failed from queue. provider={}, providerId={}",
                messagePayload.provider,
                messagePayload.providerId,
                e
            )
            throw e
        }
    }
}
