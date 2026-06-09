package kdh.domain.routine.service

import kdh.domain.routine.dto.*
import kdh.domain.routine.enum.*
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.domain.user.repository.UserRepository
import kdh.domain.user.repository.UserProfileHistoryRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@SpringBootTest
class RoutineGenerationPerformanceTest {

    @Autowired
    lateinit var routineGenerationService: RoutineGenerationService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var userProfileHistoryRepository: UserProfileHistoryRepository

    @Autowired
    lateinit var routineRepository: RoutineRepository

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    private fun deleteRoutinesOnly(provider: String, providerId: String) {
        routineRepository.deleteExerciseDetailsByUser(provider, providerId)
        routineRepository.deleteWorkoutSectionsByUser(provider, providerId)
        routineRepository.deleteDailyWorkoutsByUser(provider, providerId)
        routineRepository.deleteRoutinesByUser(provider, providerId)
    }

    private fun deleteUserAndRoutines(provider: String, providerId: String) {
        deleteRoutinesOnly(provider, providerId)
        userProfileHistoryRepository.deleteByUserProviderAndUserProviderId(provider, providerId)
        userRepository.deleteByProviderAndProviderId(provider, providerId)
    }

    @Test
    fun runRoutineGenerationPerformanceTest() {
        val provider = "performance"
        val totalIterations = 10

        println("==================================================")
        println(" 시작: 루틴 생성 병렬 성능 테스트 (총 $totalIterations 회)")
        println("==================================================")

        // 1. 테스트 사용자 설정 (기존 데이터 초기화 및 신규 생성)
        transactionTemplate.execute {
            for (i in 1..totalIterations) {
                val providerId = "perf-user-$i"
                deleteUserAndRoutines(provider, providerId)
            }
        }

        transactionTemplate.execute {
            for (i in 1..totalIterations) {
                val providerId = "perf-user-$i"
                val name = "Perf User $i"
                val user = userRepository.save(User(providerId = providerId, provider = provider, name = name))
                userProfileHistoryRepository.save(
                    UserProfileHistory(
                        user = user,
                        heightCm = 175.0,
                        weightKg = 70.0,
                        gender = Gender.MALE
                    )
                )
            }
        }

        // 2. 루틴 생성 요청 구성 (1주일 분량, 주 3회 운동 MON, WED, FRI)
        val request = RoutineCreateRequest(
            fcmToken = "sample-fcm-token",
            goal = GoalSection(
                goalType = GoalType.HEALTH_CARE,
                targetWeight = null,
                targetBodyParts = listOf()
            ),
            fitnessLevel = FitnessLevel.BEGINNER,
            schedule = ScheduleSection(
                totalWeeks = 1,
                hoursPerDay = 1.0,
                activeDays = listOf(DayOfWeek.MON, DayOfWeek.WED, DayOfWeek.FRI)
            ),
            preferredExerciseTypes = listOf(ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(
                locations = listOf(LocationType.HOME),
                equipments = listOf(EquipmentType.BAND)
            )
        )

        val executor = Executors.newFixedThreadPool(totalIterations)
        val startTotalTime = System.currentTimeMillis()

        class RunResult(val index: Int, val durationMs: Long, val isSuccess: Boolean, val errorMessage: String?)

        val futures = (1..totalIterations).map { i ->
            val providerId = "perf-user-$i"
            CompletableFuture.supplyAsync({
                println("▶ [$i / $totalIterations] 회차 루틴 생성 시작...")
                val startTime = System.currentTimeMillis()
                try {
                    // 각 사용자의 이전 생성 기록 삭제
                    transactionTemplate.execute {
                        deleteRoutinesOnly(provider, providerId)
                    }
                    routineGenerationService.generateMultiWeekRoutine(request, provider, providerId)
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    println("✓ [$i / $totalIterations] 성공 - 소요시간: ${duration / 1000.0}초")
                    RunResult(i, duration, true, null)
                } catch (e: Exception) {
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    println("✗ [$i / $totalIterations] 실패 - 소요시간: ${duration / 1000.0}초, 에러: ${e.message}")
                    RunResult(i, duration, false, e.message)
                }
            }, executor)
        }

        // 모든 병렬 작업 완료 대기
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        val endTotalTime = System.currentTimeMillis()
        executor.shutdown()

        val results = futures.map { it.get() }.sortedBy { it.index }

        // 3. 테스트 계정 및 루틴 클린업
        transactionTemplate.execute {
            for (i in 1..totalIterations) {
                val providerId = "perf-user-$i"
                deleteUserAndRoutines(provider, providerId)
            }
        }

        // 4. 통계 계산 및 파일 저장
        val successResults = results.filter { it.isSuccess }
        val successCount = successResults.size
        val failureCount = totalIterations - successCount

        val minTime = successResults.map { it.durationMs }.minOrNull() ?: 0L
        val maxTime = successResults.map { it.durationMs }.maxOrNull() ?: 0L
        val avgTime = if (successResults.isNotEmpty()) successResults.map { it.durationMs }.average() else 0.0
        val totalWallClockTime = endTotalTime - startTotalTime

        val summaryReport = """
            ==================================================
                       루틴 생성 API 병렬 성능 테스트 결과
            ==================================================
            테스트 시각   : ${LocalDateTime.now()}
            총 실행 횟수  : $totalIterations (병렬 수행)
            성공 횟수     : $successCount
            실패 횟수     : $failureCount
            성공률        : ${(successCount.toDouble() / totalIterations * 100).toInt()}%
            
            [소요 시간 통계]
            총 병렬 소요시간(Wall-clock): ${totalWallClockTime / 1000.0}초 (${String.format("%.2f", totalWallClockTime / 1000.0 / 60.0)}분)
            평균 성공 소요 시간 : ${String.format("%.2f", avgTime / 1000.0)}초
            최소 성공 소요 시간 : ${minTime / 1000.0}초
            최대 성공 소요 시간 : ${maxTime / 1000.0}초
            
            [상세 실행 기록]
            ${results.map { run ->
                val statusText = if (run.isSuccess) "성공" else "실패 (에러: ${run.errorMessage})"
                "회차 ${run.index}: ${run.durationMs / 1000.0}초 - $statusText"
            }.joinToString("\n")}
            ==================================================
        """.trimIndent()

        println(summaryReport)
        
        val file = File("c:/Users/user/Desktop/pj/KDH/performance_test_result.txt")
        file.writeText(summaryReport)
        println("성능 테스트 결과를 파일에 성공적으로 저장했습니다: ${file.absolutePath}")
    }
}
