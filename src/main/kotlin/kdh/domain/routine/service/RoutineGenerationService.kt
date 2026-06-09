package kdh.domain.routine.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kdh.domain.routine.client.WorkoutApiClient
import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.entity.DailyWorkout
import kdh.domain.routine.entity.ExerciseDetail
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.entity.WorkoutSection
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.routine.exception.InvalidRoutineGenerationResultException
import kdh.domain.user.repository.UserRepository
import kdh.domain.user.exception.UserNotFoundException
import kdh.infra.fcm.FcmService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RoutineGenerationService(
    private val workoutApiClient: WorkoutApiClient,
    private val fcmService: FcmService,
    private val routineRepository: RoutineRepository,
    private val userRepository: UserRepository
) {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)
    private companion object {
        const val PHASE_MULTI_WEEK_GENERATING = "MULTI_WEEK_GENERATING"
        const val PHASE_PERSISTING_WEEKS = "PERSISTING_WEEKS"
        const val PHASE_COMPLETED = "COMPLETED"
        const val MAX_PARALLEL_WEEK_GENERATION = 4
    }

    fun generateMultiWeekRoutine(
        request: RoutineCreateRequest,
        provider: String,
        providerId: String,
        feedback: String? = null,
        regenerationCount: Int = 0
    ) {
        val startedAt = System.currentTimeMillis()
        val targetWorkoutCount = request.schedule.totalWeeks * request.schedule.activeDays.size
        val timingEstimate = estimateRoutineTiming(request, targetWorkoutCount)

        log.info(
            "Routine generation started. provider={}, providerId={}, totalWeeks={}, activeDays={}, hoursPerDay={}, goalType={}, targetWeight={}, targetBodyParts={}, fitnessLevel={}, preferredExerciseTypes={}, locations={}, equipments={}, targetWorkoutCount={}",
            provider,
            providerId,
            request.schedule.totalWeeks,
            request.schedule.activeDays,
            request.schedule.hoursPerDay,
            request.goal.goalType,
            request.goal.targetWeight,
            request.goal.targetBodyParts,
            request.fitnessLevel,
            request.preferredExerciseTypes,
            request.environment.locations,
            request.environment.equipments,
            targetWorkoutCount
        )
        sendRoutineProgress(
            token = request.fcmToken,
            status = "STARTED",
            phase = PHASE_MULTI_WEEK_GENERATING,
            createdCount = 0,
            totalCount = targetWorkoutCount,
            timingEstimate = timingEstimate,
            startedAtMillis = startedAt,
            notificationTitle = "루틴 생성 시작",
            notificationBody = "맞춤 운동 루틴을 만들기 시작했어요."
        )

        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw UserNotFoundException(provider, providerId)

        log.info(
            "Routine generation owner found. provider={}, providerId={}, userName={}",
            provider,
            providerId,
            user.name
        )

        val userId = "$provider:$providerId"
        var lastException: Exception? = null
        val maxAttempts = 3
        val attemptsHistory = mutableListOf<Routine>()

        for (attempt in 1..maxAttempts) {
            try {
                log.info("Routine generation attempt {}/{} started for userId={}", attempt, maxAttempts, userId)
                val routineStartDate = LocalDate.now()
                val routine = Routine(
                    user = user,
                    totalWeeks = request.schedule.totalWeeks,
                    startDate = routineStartDate,
                    originalRequestJson = objectMapper.writeValueAsString(request),
                    regenerationCount = regenerationCount
                )

                val apiStartedAt = System.currentTimeMillis()
                val weeklyWorkoutsByWeek = workoutApiClient.generateMultiWeekRoutine(request, userId, feedback)
                log.info(
                    "Routine multi-week generation API completed on attempt {}. userId={}, generatedWeeks={}, expectedWeeks={}, generatedDays={}, expectedDaysPerWeek={}, elapsedMs={}",
                    attempt,
                    userId,
                    weeklyWorkoutsByWeek.size,
                    request.schedule.totalWeeks,
                    weeklyWorkoutsByWeek.sumOf { it.size },
                    request.schedule.activeDays.size,
                    System.currentTimeMillis() - apiStartedAt
                )

                sendRoutineProgress(
                    token = request.fcmToken,
                    status = "GENERATING",
                    phase = PHASE_PERSISTING_WEEKS,
                    createdCount = 0,
                    totalCount = targetWorkoutCount,
                    timingEstimate = timingEstimate,
                    startedAtMillis = startedAt,
                    notificationTitle = "루틴 생성 중",
                    notificationBody = "기본 운동 계획을 준비했어요."
                )

                val workoutDatesByGenerationOrder = generateWorkoutDates(
                    startDate = routineStartDate,
                    activeDays = request.schedule.activeDays,
                    count = targetWorkoutCount
                )

                var dayCounter = 1
                for (week in 1..request.schedule.totalWeeks) {
                    val weekStartedAt = System.currentTimeMillis()
                    val workoutDates = workoutDatesByGenerationOrder
                        .drop((week - 1) * request.schedule.activeDays.size)
                        .take(request.schedule.activeDays.size)
                    val weeklyWorkoutsJson = weeklyWorkoutsByWeek.getOrElse(week - 1) { emptyList() }
                    val progressedWeeklyWorkoutsJson = applyWeeklyProgression(weeklyWorkoutsJson, week)

                    for ((index, workoutJson) in progressedWeeklyWorkoutsJson.withIndex()) {
                        val workoutDate = workoutDates.getOrNull(index) ?: routineStartDate.plusDays((dayCounter - 1).toLong())
                        val dailyWorkout = parseAndCreateDailyWorkout(workoutJson, dayCounter, workoutDate)
                        routine.addDailyWorkout(dailyWorkout)
                        dayCounter += 1
                    }

                    log.info(
                        "Routine week expansion completed. attempt={}, week={}, userId={}, generatedDays={}, expectedDays={}, elapsedMs={}",
                        attempt,
                        week,
                        userId,
                        weeklyWorkoutsJson.size,
                        request.schedule.activeDays.size,
                        System.currentTimeMillis() - weekStartedAt
                    )

                    // 매 주차가 생성될 때마다 완료도와 남은 분량 전송 (마지막 주차는 최종 완료 시 전송)
                    if (week < request.schedule.totalWeeks) {
                        val progressPercent = calculateProgressPercent(routine.dailyWorkouts.size, targetWorkoutCount)
                        sendRoutineProgress(
                            token = request.fcmToken,
                            status = "GENERATING",
                            phase = PHASE_PERSISTING_WEEKS,
                            createdCount = routine.dailyWorkouts.size,
                            totalCount = targetWorkoutCount,
                            timingEstimate = timingEstimate,
                            startedAtMillis = startedAt,
                            notificationTitle = "루틴 생성 중 ($progressPercent%)",
                            notificationBody = "${week}주차 운동 계획이 준비되었어요. 전체 ${request.schedule.totalWeeks}주 중 ${week}주 완료!"
                        )
                    }
                }

                // 불완전한 운동명/수행횟수 누락 운동 사전 제거
                filterInvalidExercises(routine)

                // 3회 모두 실패할 경우 복구를 위해 필터링된 루틴 객체 보관
                attemptsHistory.add(routine)

                // AI 응답 검증 (한글 검증 및 정상 루틴 데이터 구조 검증)
                validateRoutine(routine, targetWorkoutCount)

                // 최종 성공 시 DB 저장 및 완료 메시지 1회 발송
                val savedRoutine = routineRepository.saveAndFlush(routine)
                log.info(
                    "Routine generation success on attempt {}/{}. routineId={}, provider={}, providerId={}, dailyWorkoutCount={}, elapsedMs={}",
                    attempt,
                    maxAttempts,
                    savedRoutine.id,
                    provider,
                    providerId,
                    savedRoutine.dailyWorkouts.size,
                    System.currentTimeMillis() - startedAt
                )

                sendRoutineProgress(
                    token = request.fcmToken,
                    status = "COMPLETED",
                    phase = PHASE_COMPLETED,
                    createdCount = savedRoutine.dailyWorkouts.size,
                    totalCount = targetWorkoutCount,
                    timingEstimate = timingEstimate,
                    startedAtMillis = startedAt,
                    notificationTitle = "루틴 생성 완료!",
                    notificationBody = "${request.schedule.totalWeeks}주 동안의 맞춤 운동 루틴이 준비됐어요."
                )
                return

            } catch (e: Exception) {
                log.warn(
                    "[Attempt {}/{}] Routine validation or API generation failed. provider={}, providerId={}, reason={}",
                    attempt,
                    maxAttempts,
                    provider,
                    providerId,
                    e.message,
                    e
                )
                lastException = e
            }
        }

        // 3회 실패 시 최종 오류 로그 및 예외 투척 전 Fallback 복구 시도
        log.warn("All {} routine generation attempts failed validation. Attempting fallback recovery. provider={}, providerId={}", maxAttempts, provider, providerId)
        val bestRoutine = attemptsHistory.maxByOrNull { r ->
            r.dailyWorkouts.flatMap { it.sections }.flatMap { it.exercises }.size
        }

        if (bestRoutine != null && bestRoutine.dailyWorkouts.flatMap { it.sections }.flatMap { it.exercises }.isNotEmpty()) {
            val remainingExerciseCount = bestRoutine.dailyWorkouts.flatMap { it.sections }.flatMap { it.exercises }.size
            log.info(
                "Fallback recovery selected routine with {} remaining exercises. Saving routine by bypassing structural validation. provider={}, providerId={}",
                remainingExerciseCount,
                provider,
                providerId
            )
            val savedRoutine = routineRepository.saveAndFlush(bestRoutine)

            sendRoutineProgress(
                token = request.fcmToken,
                status = "COMPLETED",
                phase = PHASE_COMPLETED,
                createdCount = savedRoutine.dailyWorkouts.size,
                totalCount = targetWorkoutCount,
                timingEstimate = timingEstimate,
                startedAtMillis = startedAt,
                notificationTitle = "루틴 생성 완료!",
                notificationBody = "${request.schedule.totalWeeks}주 동안의 맞춤 운동 루틴이 준비됐어요."
            )
            return
        }

        log.error("Routine generation failed after {} attempts and fallback recovery also failed. provider={}, providerId={}", maxAttempts, provider, providerId)
        throw lastException ?: InvalidRoutineGenerationResultException("AI 운동 계획 생성 및 검증에 모두 실패했습니다.")
    }

    private fun validateRoutine(routine: Routine, targetWorkoutCount: Int) {
        val workouts = routine.dailyWorkouts
        val totalExercises = workouts.flatMap { it.sections }.flatMap { it.exercises }

        // 1. 운동 개수 검증
        if (totalExercises.isEmpty()) {
            throw InvalidRoutineGenerationResultException("생성된 운동 리스트가 비어있습니다.")
        }

        // 2. 섹션 개수 검증 (각 운동 날짜에 최소 1개 섹션 존재)
        for (dw in workouts) {
            if (dw.sections.isEmpty()) {
                throw InvalidRoutineGenerationResultException("Day ${dw.day}에 운동 섹션(준비 운동 등)이 존재하지 않습니다.")
            }
        }

        // 3. 정확한 운동 일수 정합성 검증
        if (workouts.size != targetWorkoutCount) {
            throw InvalidRoutineGenerationResultException("생성된 운동 일수(${workouts.size})가 목표 설정치($targetWorkoutCount)와 일치하지 않습니다.")
        }

        // 4. 운동명 유효 글자수 검증 (비어있음, '이름 없음', 2글자 미만 차단)
        for (ex in totalExercises) {
            val name = ex.exerciseName.trim()
            if (name.isBlank() || name == "이름 없음" || name.length < 2) {
                throw InvalidRoutineGenerationResultException("부적절하거나 너무 짧은 운동명(2글자 미만)이 포함되어 있습니다: '${ex.exerciseName}'")
            }
        }

        // 5. 허용 문자 정규식 검증 (영어, 한국어, 숫자, 공백, 표준 특수문자만 허용)
        val allowedRegex = Regex("""^[a-zA-Z0-9ㄱ-ㅎㅏ-ㅣ가-힣\s!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~]+$""")
        for (ex in totalExercises) {
            if (!ex.exerciseName.matches(allowedRegex)) {
                throw InvalidRoutineGenerationResultException(
                    "운동명에 허용되지 않은 문자(한자, 일어, 이모지 등)가 포함되어 있습니다: '${ex.exerciseName}'"
                )
            }
        }

        // 6. 한글 비율 90% 이상 검증 (전체 운동 중 최소 1글자 이상 한글 포함 비율)
        val koreanExercisesCount = totalExercises.count { it.exerciseName.contains(Regex("[ㄱ-ㅎㅏ-ㅣ가-힣]")) }
        val koreanRatio = koreanExercisesCount.toDouble() / totalExercises.size.toDouble()
        if (koreanRatio < 0.9) {
            throw InvalidRoutineGenerationResultException(
                "한글이 포함된 운동명의 비율이 90% 미만입니다. ($koreanExercisesCount/${totalExercises.size})"
            )
        }

        // 7. 수행 방법(강도/횟수) 필수화 검증 (repsTime 누락 비율 10% 이상 차단)
        val invalidRepsCount = totalExercises.count { it.repsTime.isNullOrBlank() || it.repsTime?.trim() == "값 없음" }
        val invalidRepsRatio = invalidRepsCount.toDouble() / totalExercises.size.toDouble()
        if (invalidRepsRatio >= 0.10) {
            throw InvalidRoutineGenerationResultException(
                "수행 방법(강도/횟수)이 누락되었거나 불완전한 운동의 비율이 10% 이상입니다. ($invalidRepsCount/${totalExercises.size})"
            )
        }

        // 8. 인코딩 깨짐 및 자모음 나열 차단 검증
        val brokenOrJamoRegex = Regex("""\uFFFD|[ㄱ-ㅎ]{2,}|[ㅏ-ㅣ]{2,}""")
        for (ex in totalExercises) {
            if (ex.exerciseName.contains(brokenOrJamoRegex)) {
                throw InvalidRoutineGenerationResultException(
                    "운동명에 인코딩 깨짐() 또는 완성되지 않은 자모음 나열이 포함되어 있습니다: '${ex.exerciseName}'"
                )
            }
        }
        for (dw in workouts) {
            for (sec in dw.sections) {
                if (sec.name.contains(brokenOrJamoRegex)) {
                    throw InvalidRoutineGenerationResultException(
                        "섹션명에 인코딩 깨짐() 또는 완성되지 않은 자모음 나열이 포함되어 있습니다: '${sec.name}'"
                    )
                }
            }
        }
    }

    private fun parseAndCreateDailyWorkout(
        workoutJson: Map<String, Any>,
        day: Int,
        workoutDate: LocalDate
    ): DailyWorkout {
        val dailyWorkout = DailyWorkout(day = day, workoutDate = workoutDate)

        workoutJson.forEach { (sectionName, exercisesRaw) ->
            if (exercisesRaw !is List<*>) {
                log.warn(
                    "Skipping unexpected workout section payload. day={}, workoutDate={}, sectionName={}, payloadType={}",
                    day,
                    workoutDate,
                    sectionName,
                    exercisesRaw.javaClass.name
                )
                return@forEach
            }

            val workoutSection = WorkoutSection(name = sectionName)
            val exercisesList = objectMapper.convertValue(
                exercisesRaw,
                object : TypeReference<List<Map<String, Any>>>() {}
            )

            exercisesList
                .mapIndexed { index, exerciseMap ->
                    val exercise = createExerciseDetail(exerciseMap)
                    log.debug(
                        "Exercise parsed. day={}, workoutDate={}, sectionName={}, exerciseIndex={}, exerciseName={}, repsTime={}, sourceKeys={}",
                        day,
                        workoutDate,
                        sectionName,
                        index,
                        exercise.exerciseName,
                        exercise.repsTime,
                        exerciseMap.keys
                    )
                    exercise
                }
                .forEach(workoutSection::addExercise)

            dailyWorkout.addSection(workoutSection)
            log.info(
                "Workout section parsed. day={}, workoutDate={}, sectionName={}, exerciseCount={}",
                day,
                workoutDate,
                sectionName,
                workoutSection.exercises.size
            )
        }

        if (dailyWorkout.sections.isEmpty()) {
            log.warn(
                "Daily workout parsed with no sections. day={}, workoutDate={}, sourceKeys={}",
                day,
                workoutDate,
                workoutJson.keys
            )
        }

        return dailyWorkout
    }

    private fun createExerciseDetail(exerciseMap: Map<String, Any>): ExerciseDetail {
        return ExerciseDetail(
            exerciseName = exerciseMap["exercise_name"] as? String
                ?: exerciseMap["exerciseName"] as? String
                ?: exerciseMap["exercise"] as? String
                ?: "이름 없음",
            repsTime = exerciseMap["reps_time"]?.toString()
                ?: exerciseMap["repsTime"]?.toString()
                ?: exerciseMap["reps"]?.toString()
        )
    }

    private fun generateWorkoutDates(
        startDate: LocalDate,
        activeDays: List<kdh.domain.routine.enum.DayOfWeek>,
        count: Int
    ): List<LocalDate> {
        val activeJavaDays = activeDays.map { it.toJavaDayOfWeek() }.toSet()
        val workoutDates = mutableListOf<LocalDate>()
        var cursor = startDate

        while (workoutDates.size < count) {
            if (cursor.dayOfWeek in activeJavaDays) {
                workoutDates.add(cursor)
            }
            cursor = cursor.plusDays(1)
        }

        return workoutDates
    }

    private fun kdh.domain.routine.enum.DayOfWeek.toJavaDayOfWeek(): java.time.DayOfWeek {
        return when (this) {
            kdh.domain.routine.enum.DayOfWeek.MON -> java.time.DayOfWeek.MONDAY
            kdh.domain.routine.enum.DayOfWeek.TUE -> java.time.DayOfWeek.TUESDAY
            kdh.domain.routine.enum.DayOfWeek.WED -> java.time.DayOfWeek.WEDNESDAY
            kdh.domain.routine.enum.DayOfWeek.THU -> java.time.DayOfWeek.THURSDAY
            kdh.domain.routine.enum.DayOfWeek.FRI -> java.time.DayOfWeek.FRIDAY
            kdh.domain.routine.enum.DayOfWeek.SAT -> java.time.DayOfWeek.SATURDAY
            kdh.domain.routine.enum.DayOfWeek.SUN -> java.time.DayOfWeek.SUNDAY
        }
    }

    private fun applyWeeklyProgression(
        baseWeeklyWorkouts: List<Map<String, Any>>,
        week: Int
    ): List<Map<String, Any>> {
        if (week <= 1) {
            return baseWeeklyWorkouts
        }

        return baseWeeklyWorkouts.map { workoutJson ->
            workoutJson.mapValues { (_, exercisesRaw) ->
                if (exercisesRaw !is List<*>) {
                    exercisesRaw
                } else {
                    exercisesRaw.map { exerciseRaw ->
                        if (exerciseRaw !is Map<*, *>) {
                            exerciseRaw
                        } else {
                            val progressedExercise = LinkedHashMap<String, Any?>()
                            exerciseRaw.forEach { (key, value) ->
                                if (key is String) {
                                    progressedExercise[key] = value
                                }
                            }
                            val baseRepsTime = progressedExercise["reps_time"]
                                ?: progressedExercise["repsTime"]
                                ?: progressedExercise["reps"]
                            progressedExercise["reps_time"] = progressRepsTime(baseRepsTime?.toString(), week)
                            progressedExercise
                        }
                    }
                }
            }
        }
    }

    private fun progressRepsTime(baseRepsTime: String?, week: Int): String {
        if (baseRepsTime.isNullOrBlank()) return ""
        val trimmed = baseRepsTime.trim()
        if (trimmed == "값 없음") return trimmed

        // 1. Check if sets format: e.g., "3세트 10회" or "3세트 10~12회"
        val setsRegex = Regex("""^(\d+)\s*세트\s*(.*)$""")
        val setsMatch = setsRegex.matchEntire(trimmed)

        val hasSets = setsMatch != null
        val sets = setsMatch?.groupValues?.get(1)?.toInt() ?: 0
        val remainder = if (hasSets) setsMatch!!.groupValues[2].trim() else trimmed

        val progressedSets = if (hasSets) sets + (week - 1) / 2 else 0

        // 2. Check if remainder is Time-based (contains 분, 초, min, sec, etc.)
        val minutesRegex = Regex("""(\d+)\s*(분|min(?:ute)?s?)""", RegexOption.IGNORE_CASE)
        val secondsRegex = Regex("""(\d+)\s*(초|sec(?:ond)?s?)""", RegexOption.IGNORE_CASE)

        val minMatch = minutesRegex.find(remainder)
        val secMatch = secondsRegex.find(remainder)

        val isTimeBased = minMatch != null || secMatch != null

        val resultRemainder = if (isTimeBased) {
            val minutes = minMatch?.groupValues?.get(1)?.toInt() ?: 0
            val minUnit = minMatch?.groupValues?.get(2) ?: "분"
            val seconds = secMatch?.groupValues?.get(1)?.toInt() ?: 0
            val secUnit = secMatch?.groupValues?.get(2) ?: "초"

            val totalSeconds = minutes * 60 + seconds
            val timeFactor = if (hasSets) {
                1.0 + (week / 2) * 0.1
            } else {
                1.0 + (week - 1) * 0.1
            }
            val progressedSeconds = Math.round(totalSeconds * timeFactor).toInt()

            // Round to nearest 5 seconds if >= 10 seconds
            var roundedSeconds = if (totalSeconds >= 10) {
                ((progressedSeconds + 2) / 5) * 5
            } else {
                progressedSeconds
            }

            // Ensure minimum increase
            val minIncreaseSeconds = if (hasSets) (week / 2) * 5 else (week - 1) * 5
            val minRequiredSeconds = totalSeconds + minIncreaseSeconds
            if (roundedSeconds < minRequiredSeconds) {
                roundedSeconds = minRequiredSeconds
            }

            // Resolve units
            val minUnitResolved = if (minMatch != null) minUnit else {
                if (secUnit.contains(Regex("[ㄱ-ㅎㅏ-ㅣ가-힣]"))) "분" else "min"
            }
            val secUnitResolved = if (secMatch != null) secUnit else {
                if (minUnit.contains(Regex("[ㄱ-ㅎㅏ-ㅣ가-힣]"))) "초" else "sec"
            }

            val newMin = roundedSeconds / 60
            val newSec = roundedSeconds % 60

            if (newMin > 0 && newSec > 0) {
                "${newMin}${minUnitResolved} ${newSec}${secUnitResolved}"
            } else if (newMin > 0) {
                "${newMin}${minUnitResolved}"
            } else {
                "${roundedSeconds}${secUnitResolved}"
            }
        } else {
            // Reps-based (reps or ranges of reps)
            val rangeRegex = Regex("""(\d+)\s*([~-])\s*(\d+)""")
            if (rangeRegex.containsMatchIn(remainder)) {
                rangeRegex.replace(remainder) { matchResult ->
                    val minReps = matchResult.groupValues[1].toInt()
                    val separator = matchResult.groupValues[2]
                    val maxReps = matchResult.groupValues[3].toInt()

                    val addMin = if (hasSets) {
                        Math.round(minReps * ((week / 2) * 10) / 100.0).toInt().coerceAtLeast(week / 2)
                    } else {
                        Math.round(minReps * ((week - 1) * 10) / 100.0).toInt().coerceAtLeast(week - 1)
                    }
                    val progressedMin = minReps + addMin

                    val addMax = if (hasSets) {
                        Math.round(maxReps * ((week / 2) * 10) / 100.0).toInt().coerceAtLeast(week / 2)
                    } else {
                        Math.round(maxReps * ((week - 1) * 10) / 100.0).toInt().coerceAtLeast(week - 1)
                    }
                    val progressedMax = maxReps + addMax

                    "$progressedMin$separator$progressedMax"
                }
            } else {
                // Single number match
                val singleNumberRegex = Regex("""\d+""")
                singleNumberRegex.replace(remainder) { matchResult ->
                    val reps = matchResult.value.toInt()
                    val add = if (hasSets) {
                        Math.round(reps * ((week / 2) * 10) / 100.0).toInt().coerceAtLeast(week / 2)
                    } else {
                        Math.round(reps * ((week - 1) * 10) / 100.0).toInt().coerceAtLeast(week - 1)
                    }
                    val progressedReps = reps + add
                    progressedReps.toString()
                }
            }
        }

        return if (hasSets) {
            if (resultRemainder.isNotBlank()) {
                "${progressedSets}세트 $resultRemainder"
            } else {
                "${progressedSets}세트"
            }
        } else {
            resultRemainder
        }
    }

    private fun sendRoutineProgress(
        token: String?,
        status: String,
        phase: String,
        createdCount: Int,
        totalCount: Int,
        timingEstimate: RoutineTimingEstimate,
        startedAtMillis: Long,
        notificationTitle: String,
        notificationBody: String
    ) {
        val progressPercent = calculateProgressPercent(createdCount, totalCount)
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
        val firstWeekRemainingSeconds = (timingEstimate.firstWeekSeconds - elapsedSeconds).coerceAtLeast(0)
        val remainingSeconds = estimateRemainingSeconds(
            status = status,
            phase = phase,
            createdCount = createdCount,
            totalCount = totalCount,
            elapsedSeconds = elapsedSeconds,
            timingEstimate = timingEstimate
        )
        fcmService.sendNotification(
            token = token,
            title = notificationTitle,
            body = notificationBody,
            data = mapOf(
                "type" to "ROUTINE_GENERATION",
                "status" to status,
                "phase" to phase,
                "createdCount" to createdCount.toString(),
                "totalCount" to totalCount.toString(),
                "progressPercent" to progressPercent.toString(),
                "estimatedFirstWeekMinutes" to secondsToDisplayMinutes(timingEstimate.firstWeekSeconds).toString(),
                "estimatedFirstWeekRemainingMinutes" to secondsToDisplayMinutes(firstWeekRemainingSeconds).toString(),
                "estimatedTotalMinutes" to secondsToDisplayMinutes(timingEstimate.totalSeconds).toString(),
                "estimatedRemainingMinutes" to secondsToDisplayMinutes(remainingSeconds).toString(),
                "completed" to (status == "COMPLETED").toString()
            )
        )
    }

    private fun calculateProgressPercent(createdCount: Int, totalCount: Int): Int {
        if (totalCount <= 0) {
            return 0
        }
        return (createdCount * 100 / totalCount).coerceIn(0, 100)
    }

    private fun secondsToDisplayMinutes(seconds: Int): Int {
        if (seconds <= 0) {
            return 0
        }
        return (seconds + 59) / 60
    }

    private fun estimateRoutineTiming(
        request: RoutineCreateRequest,
        totalWorkoutCount: Int
    ): RoutineTimingEstimate {
        val firstWeekSeconds = estimateFirstWeekSeconds(request)
        val expansionSeconds = estimateExpansionSeconds(totalWorkoutCount)
        val generationWaves = (request.schedule.totalWeeks + MAX_PARALLEL_WEEK_GENERATION - 1) /
            MAX_PARALLEL_WEEK_GENERATION
        val multiWeekGenerationSeconds = (firstWeekSeconds * generationWaves * 135 + 99) / 100
        return RoutineTimingEstimate(
            firstWeekSeconds = firstWeekSeconds,
            totalSeconds = (multiWeekGenerationSeconds + expansionSeconds).coerceAtLeast(firstWeekSeconds)
        )
    }

    private fun estimateFirstWeekSeconds(request: RoutineCreateRequest): Int {
        val activeDaySeconds = when (request.schedule.activeDays.size) {
            1 -> 150
            2 -> 180
            3 -> 250
            4 -> 240
            5 -> 300
            6 -> 340
            else -> 390
        }
        val hourAdjustment = when {
            request.schedule.hoursPerDay <= 1.0 -> 0
            request.schedule.hoursPerDay <= 2.0 -> 15
            request.schedule.hoursPerDay <= 3.0 -> 30
            request.schedule.hoursPerDay <= 4.0 -> 45
            else -> 60
        }
        val levelAdjustment = when (request.fitnessLevel) {
            kdh.domain.routine.enum.FitnessLevel.BEGINNER -> 0
            kdh.domain.routine.enum.FitnessLevel.INTERMEDIATE -> 15
            kdh.domain.routine.enum.FitnessLevel.ADVANCED -> 30
        }
        val goalAdjustment = when (request.goal.goalType) {
            kdh.domain.routine.enum.GoalType.HEALTH_CARE -> 0
            kdh.domain.routine.enum.GoalType.DIET -> 10
            kdh.domain.routine.enum.GoalType.MUSCLE_GAIN -> 15
        }
        return (activeDaySeconds + hourAdjustment + levelAdjustment + goalAdjustment).coerceIn(150, 480)
    }

    private fun estimateExpansionSeconds(totalWorkoutCount: Int): Int {
        return (5 + totalWorkoutCount / 10).coerceIn(5, 25)
    }

    private fun estimateRemainingSeconds(
        status: String,
        phase: String,
        createdCount: Int,
        totalCount: Int,
        elapsedSeconds: Int,
        timingEstimate: RoutineTimingEstimate
    ): Int {
        if (status == "COMPLETED") {
            return 0
        }

        if (phase == PHASE_PERSISTING_WEEKS) {
            return estimateExpansionSeconds((totalCount - createdCount).coerceAtLeast(0))
        }

        return (timingEstimate.totalSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    private fun filterInvalidExercises(routine: Routine) {
        val allowedRegex = Regex("""^[a-zA-Z0-9ㄱ-ㅎㅏ-ㅣ가-힣\s!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~]+$""")
        val brokenOrJamoRegex = Regex("""\uFFFD|[ㄱ-ㅎ]{2,}|[ㅏ-ㅣ]{2,}""")

        for (dw in routine.dailyWorkouts) {
            for (sec in dw.sections) {
                val validExercises = sec.exercises.filter { ex ->
                    val name = ex.exerciseName.trim()
                    if (name.isBlank() || name == "이름 없음" || name.length < 2) return@filter false
                    if (!name.matches(allowedRegex)) return@filter false
                    if (name.contains(brokenOrJamoRegex)) return@filter false
                    val reps = ex.repsTime
                    if (reps.isNullOrBlank() || reps.trim() == "값 없음") return@filter false
                    true
                }
                sec.exercises.clear()
                validExercises.forEach { sec.addExercise(it) }
            }
        }
    }

    private data class RoutineTimingEstimate(
        val firstWeekSeconds: Int,
        val totalSeconds: Int
    )
}
