package kdh.domain.routine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.dto.RoutineCreationMessage
import kdh.domain.routine.exception.RoutineGenerationFailedException
import kdh.domain.user.exception.UserNotFoundException
import kdh.global.exception.KdhException
import kdh.infra.fcm.FcmService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RoutineMessageHandler(
    private val routineGenerationService: RoutineGenerationService,
    private val fcmService: FcmService,
    private val routineCreationTracker: RoutineCreationTracker
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = ["routine.create.queue"])
    fun handleMessage(message: String) {
        val startedAt = System.currentTimeMillis()
        log.info("Routine creation message received. payloadBytes={}", message.toByteArray().size)
        val messagePayload = objectMapper.readValue(message, RoutineCreationMessage::class.java)
        try {
            log.info(
                "Routine generation started from queue. provider={}, providerId={}, totalWeeks={}, activeDays={}, hoursPerDay={}, goalType={}, fitnessLevel={}, preferredExerciseTypes={}, locations={}, equipments={}",
                messagePayload.provider,
                messagePayload.providerId,
                messagePayload.request.schedule.totalWeeks,
                messagePayload.request.schedule.activeDays,
                messagePayload.request.schedule.hoursPerDay,
                messagePayload.request.goal.goalType,
                messagePayload.request.fitnessLevel,
                messagePayload.request.preferredExerciseTypes,
                messagePayload.request.environment.locations,
                messagePayload.request.environment.equipments
            )
            routineGenerationService.generateMultiWeekRoutine(
                request = messagePayload.request,
                provider = messagePayload.provider,
                providerId = messagePayload.providerId,
                feedback = messagePayload.feedback,
                regenerationCount = messagePayload.regenerationCount
            )
            log.info(
                "Routine generation finished from queue. provider={}, providerId={}, elapsedMs={}",
                messagePayload.provider,
                messagePayload.providerId,
                System.currentTimeMillis() - startedAt
            )
        } catch (e: UserNotFoundException) {
            log.warn(
                "Routine generation skipped because queue owner no longer exists. provider={}, providerId={}, elapsedMs={}, reason={}",
                messagePayload.provider,
                messagePayload.providerId,
                System.currentTimeMillis() - startedAt,
                e.message
            )
            sendFailureNotification(messagePayload)
        } catch (e: Exception) {
            log.error(
                "Routine generation failed from queue. provider={}, providerId={}, elapsedMs={}",
                messagePayload.provider,
                messagePayload.providerId,
                System.currentTimeMillis() - startedAt,
                e
            )
            sendFailureNotification(messagePayload)
            throw (e as? KdhException ?: RoutineGenerationFailedException(e))
        } finally {
            routineCreationTracker.decrement()
        }
    }

    private fun sendFailureNotification(messagePayload: RoutineCreationMessage) {
        val totalCount = messagePayload.request.schedule.totalWeeks * messagePayload.request.schedule.activeDays.size
        fcmService.sendNotification(
            token = messagePayload.request.fcmToken,
            title = "루틴 생성 실패",
            body = "루틴을 만드는 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
            data = mapOf(
                "type" to "ROUTINE_GENERATION",
                "status" to "FAILED",
                "phase" to "FAILED",
                "createdCount" to "0",
                "totalCount" to totalCount.toString(),
                "progressPercent" to "0",
                "estimatedFirstWeekMinutes" to "0",
                "estimatedFirstWeekRemainingMinutes" to "0",
                "estimatedTotalMinutes" to "0",
                "estimatedRemainingMinutes" to "0",
                "completed" to "false"
            )
        )
    }
}
