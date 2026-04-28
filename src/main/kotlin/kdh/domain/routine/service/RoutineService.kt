package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.routine.dto.RoutineDetailResponse
import kdh.domain.routine.dto.RoutineSummaryResponse
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.repository.UserProfileHistoryRepository
import kdh.domain.user.repository.UserRepository
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoutineService(
    private val rabbitTemplate: RabbitTemplate,
    private val userRepository: UserRepository,
    private val userProfileHistoryRepository: UserProfileHistoryRepository,
    private val routineRepository: RoutineRepository
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    fun createRoutine(request: RoutineCreateRequest, provider: String, providerId: String) {
        log.info("Validating routine owner. provider={}, providerId={}", provider, providerId)
        userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $provider/$providerId")

        if (!userProfileHistoryRepository.existsByUserProviderAndUserProviderId(provider, providerId)) {
            throw IllegalArgumentException("신체 정보가 없습니다. 키, 몸무게, 성별을 먼저 등록해야 루틴을 생성할 수 있습니다.")
        }

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

    @Transactional(readOnly = true)
    fun getMyRoutines(provider: String, providerId: String): List<RoutineSummaryResponse> {
        return routineRepository.findDistinctByUserProviderAndUserProviderIdOrderByIdDesc(provider, providerId)
            .map(RoutineSummaryResponse::from)
    }

    @Transactional(readOnly = true)
    fun getMyRoutine(id: Long, provider: String, providerId: String): RoutineDetailResponse {
        val routine = routineRepository.findByIdAndUserProviderAndUserProviderId(id, provider, providerId)
            ?: throw IllegalArgumentException("루틴을 찾을 수 없습니다: $id")

        return RoutineDetailResponse.from(routine)
    }
}
