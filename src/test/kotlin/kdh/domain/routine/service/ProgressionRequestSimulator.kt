package kdh.domain.routine.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import kdh.domain.routine.dto.*
import kdh.domain.routine.enum.*
import kdh.domain.routine.entity.Routine
import kdh.domain.routine.repository.RoutineRepository
import kdh.domain.user.entity.User
import kdh.domain.user.entity.UserProfileHistory
import kdh.domain.user.enum.Gender
import kdh.domain.user.repository.UserRepository
import kdh.domain.user.repository.UserProfileHistoryRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.*

@SpringBootTest
class ProgressionRequestSimulator {

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var userProfileHistoryRepository: UserProfileHistoryRepository

    @Autowired
    lateinit var routineRepository: RoutineRepository

    @Autowired
    lateinit var transactionTemplate: org.springframework.transaction.support.TransactionTemplate

    @Test
    fun simulateProgressionRequest() {
        val provider = "google"
        val providerId = "test-user-sim-12345"
        val name = "Simulated User"
        
        transactionTemplate.execute {
            // 1. Ensure user and profile exist in MySQL DB
            val user = userRepository.findByProviderAndProviderId(provider, providerId)
                ?: userRepository.save(User(providerId = providerId, provider = provider, name = name))
                
            routineRepository.deleteByUserProviderAndUserProviderId(provider, providerId)

            if (!userProfileHistoryRepository.existsByUserProviderAndUserProviderId(provider, providerId)) {
                userProfileHistoryRepository.save(
                    UserProfileHistory(
                        user = user,
                        heightCm = 180.0,
                        weightKg = 75.0,
                        gender = Gender.MALE
                    )
                )
            }
        }
        
        // 2. Generate JWT token
        val secret = "qwertyuiop1234567890asdfghjklzxcvbnm"
        val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())
        val now = Date()
        val validity = Date(now.time + 3600000)
        val token = Jwts.builder()
            .subject(providerId)
            .claim("provider", provider)
            .claim("name", name)
            .issuedAt(now)
            .expiration(validity)
            .signWith(secretKey)
            .compact()
            
        println("Generated JWT Token: $token")
        
        // 3. Create a broad and large scope routine request (8 weeks, 5 days/week)
        val request = RoutineCreateRequest(
            fcmToken = "sample-fcm-token",
            goal = GoalSection(
                goalType = GoalType.MUSCLE_GAIN,
                targetWeight = null,
                targetBodyParts = listOf(BodyPart.CHEST, BodyPart.BACK, BodyPart.THIGH)
            ),
            fitnessLevel = FitnessLevel.ADVANCED,
            schedule = ScheduleSection(
                totalWeeks = 8,
                hoursPerDay = 1.5,
                activeDays = listOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI)
            ),
            preferredExerciseTypes = listOf(ExerciseType.STRENGTH, ExerciseType.BODYWEIGHT),
            environment = EnvironmentSection(
                locations = listOf(LocationType.GYM),
                equipments = listOf(EquipmentType.BARBELL, EquipmentType.DUMBBELL, EquipmentType.MACHINE)
            )
        )
        
        // 4. Send request to localhost:8080/routines
        val url = "http://localhost:8080/routines"
        val restTemplate = RestTemplate()
        
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        
        val entity = HttpEntity(request, headers)
        
        try {
            val response = restTemplate.postForEntity(url, entity, Void::class.java)
            println("Request successfully sent to 8080! Status code: ${response.statusCode}")
        } catch (e: Exception) {
            println("Failed to send request: ${e.message}")
            e.printStackTrace()
        }
    }

    @Test
    fun verifyGeneratedRoutine() {
        val provider = "google"
        val providerId = "test-user-sim-12345"
        
        println("Waiting for routine to be generated in the database...")
        var routine: Routine? = null
        for (i in 1..40) {
            transactionTemplate.execute {
                val r = routineRepository.findFirstByUserProviderAndUserProviderIdOrderByStartDateDesc(provider, providerId)
                if (r != null) {
                    // Initialize lazy collections
                    r.dailyWorkouts.forEach { dw ->
                        dw.sections.forEach { sec ->
                            sec.exercises.size
                        }
                    }
                    routine = r
                }
            }
            if (routine != null && routine!!.dailyWorkouts.isNotEmpty()) {
                break
            }
            Thread.sleep(3000)
        }
        
        val finalRoutine = routine
        if (finalRoutine == null) {
            println("No routine found in database!")
            return
        }
        
        val sb = StringBuilder()
        sb.appendLine("=== 생성된 루틴 결과 (8주차, 주 5일) ===")
        sb.appendLine("루틴 ID: ${finalRoutine.id}")
        sb.appendLine("시작 날짜: ${finalRoutine.startDate}")
        sb.appendLine("총 주차: ${finalRoutine.totalWeeks}")
        sb.appendLine("생성 일수: ${finalRoutine.dailyWorkouts.size}")
        
        val workouts = finalRoutine.dailyWorkouts.sortedBy { it.day }
        for (w in 1..8) {
            sb.appendLine("\n==================================")
            sb.appendLine(" [${w}주차] ")
            sb.appendLine("==================================")
            
            val weekWorkouts = workouts.filter { it.day in ((w - 1) * 5 + 1)..(w * 5) }
            for (dw in weekWorkouts) {
                sb.appendLine("\nDay ${dw.day} (${dw.workoutDate}):")
                for (sec in dw.sections) {
                    sb.appendLine("  [${sec.name}]")
                    for (ex in sec.exercises) {
                        sb.appendLine("    - ${ex.exerciseName}: ${ex.repsTime}")
                    }
                }
            }
        }
        
        val fileContent = sb.toString()
        println(fileContent)
        java.io.File("c:/Users/user/Desktop/pj/KDH/simulated_routine_result.txt").writeText(fileContent)
        println("Result successfully written to simulated_routine_result.txt!")
    }
}
