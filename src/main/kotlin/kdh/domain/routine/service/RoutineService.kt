package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.user.repository.UserRepository
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RoutineService(
    private val rabbitTemplate: RabbitTemplate,
    private val userRepository: UserRepository
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    fun createRoutine(request: RoutineCreateRequest, provider: String, providerId: String) {
        log.info("Validating routine owner. provider={}, providerId={}", provider, providerId)
        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $provider/$providerId")

        val messagePayload = RoutineCreationMessage(provider = provider, providerId = providerId, request = request)
        val message = objectMapper.writeValueAsString(messagePayload)
        log.info(
            "Publishing routine creation message. exchange={}, routingKey={}, provider={}, providerId={}, payloadBytes={}",
            "routine.exchange",
            "routine.create.key",
            provider,
            providerId,
            message.toByteArray().size
        )
        rabbitTemplate.convertAndSend("routine.exchange", "routine.create.key", message)
        log.info("Routine creation message published. provider={}, providerId={}", provider, providerId)
    }
}
