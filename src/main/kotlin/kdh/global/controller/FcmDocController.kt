package kdh.global.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kdh.domain.routine.dto.RoutineDateResponse
import kdh.domain.routine.dto.RoutineWorkoutItemResponse
import kdh.infra.fcm.FcmService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/docs/fcm")
@Tag(name = "FCM_TEST_AND_SPECIFICATION", description = "FCM 푸시 알림 실제 테스트 및 데이터 규격 명세 API")
class FcmDocController(
    private val fcmService: FcmService
) {

    @PostMapping("/test/routine-progress")
    @Operation(
        summary = "FCM 실전 테스트: 루틴 생성 진행 상태 및 완료 알림",
        description = """
            ### 🔓 [로그인 미필수 API - 자물쇠 해제]
            본 테스트 API는 로그인 인증 헤더(JWT Bearer)가 없어도 누구나 자유롭게 호출할 수 있습니다.
            
            ### ⚙️ [내부 동작 설명]
            1. 입력받은 FCM 토큰(`fcmToken`)과 요청 데이터(`status`, `phase`, `week`, `totalWeeks`)를 파싱합니다.
            2. 실제 서비스(`RoutineGenerationService`) 내부 알고리즘과 **100% 동일한 로직**을 거쳐 한글 템플릿 타이틀 및 본문을 생성합니다.
            3. `createdCount`와 `totalCount` 값 역시 내부 로직과 연동되어 `(주차 * 3)` 형태로 시뮬레이션되어 동적으로 자동 환산됩니다.
            4. 생성 완료 상태인 `COMPLETED`를 입력하면 자동으로 진행률 `100%`, 완료 여부 `completed: "true"`로 가공된 JSON이 빌드됩니다.
            5. 최종적으로 Google Firebase Admin SDK의 `FirebaseMessaging.send(message)` 클라이언트를 호출해 실 디바이스로 즉시 전달합니다.
            
            ### 💡 [상태 값 조합 가이드]
            * **STARTED (생성 시작)**: status='STARTED', phase='MULTI_WEEK_GENERATING', progressPercent='0'
            * **GENERATING (주차별 진행 중)**: status='GENERATING', phase='PERSISTING_WEEKS', progressPercent='진행 비율(예: 50%)'
            * **COMPLETED (완료)**: status='COMPLETED', phase='COMPLETED', progressPercent='100'
        """,
        security = [] // 로그인 토큰 요구 제외 (Swagger 자물쇠 풀림)
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "FCM 푸시 알림 발송 성공",
                content = [Content(schema = Schema(implementation = FcmTestResponse::class))]
            )
        ]
    )
    fun testRoutineProgress(
        @RequestBody request: FcmTestRoutineProgressRequest
    ): ResponseEntity<FcmTestResponse> {
        val title: String
        val body: String
        val totalCount = request.totalWeeks * 3
        val createdCount = if (request.status == "COMPLETED") totalCount else (request.week * 3).coerceAtMost(totalCount)
        val progressPercent = if (request.status == "COMPLETED") 100 else (request.week * 100 / request.totalWeeks).coerceIn(0, 100)

        when (request.status) {
            "STARTED" -> {
                title = "루틴 생성 시작"
                body = "맞춤 운동 루틴을 만들기 시작했어요."
            }
            "GENERATING" -> {
                if (request.phase == "MULTI_WEEK_GENERATING" || request.week == 0) {
                    title = "루틴 생성 중"
                    body = "기본 운동 계획을 준비했어요."
                } else {
                    title = "루틴 생성 중 ($progressPercent%)"
                    body = "${request.week}주차 운동 계획이 준비되었어요. 전체 ${request.totalWeeks}주 중 ${request.week}주 완료!"
                }
            }
            "COMPLETED" -> {
                title = "루틴 생성 완료!"
                body = "${request.totalWeeks}주 동안의 맞춤 운동 루틴이 준비됐어요."
            }
            else -> {
                title = "루틴 생성 중"
                body = "맞춤 운동 계획을 준비하고 있습니다."
            }
        }

        val data = mapOf(
            "type" to "ROUTINE_GENERATION",
            "status" to request.status,
            "phase" to request.phase,
            "createdCount" to createdCount.toString(),
            "totalCount" to totalCount.toString(),
            "progressPercent" to progressPercent.toString(),
            "estimatedFirstWeekMinutes" to "5",
            "estimatedFirstWeekRemainingMinutes" to "0",
            "estimatedTotalMinutes" to "8",
            "estimatedRemainingMinutes" to "0",
            "completed" to (request.status == "COMPLETED").toString()
        )

        fcmService.sendNotification(request.fcmToken, title, body, data)

        return ResponseEntity.ok(
            FcmTestResponse(
                status = "SUCCESS",
                message = "[$title] FCM 푸시 전송 성공! (수신 기기의 트레이 및 앱 내 data 로그를 확인해 주세요)"
            )
        )
    }

    @PostMapping("/test/routine-failure")
    @Operation(
        summary = "FCM 실전 테스트: 루틴 생성 실패 알림",
        description = """
            ### 🔓 [로그인 미필수 API - 자물쇠 해제]
            본 테스트 API는 로그인 인증 헤더(JWT Bearer)가 없어도 누구나 자유롭게 호출할 수 있습니다.
            
            ### ⚙️ [내부 동작 설명]
            1. 입력받은 FCM 토큰(`fcmToken`)과 루틴 생성 대상 주차(`totalWeeks`)를 읽어 들입니다.
            2. 메시지 큐 비동기 빌드 실패 핸들러(`RoutineMessageHandler.sendFailureNotification`)와 **동일한 로직**을 모방하여 한글 알림을 세팅합니다.
            3. 데이터 맵의 `status`와 `phase`는 `"FAILED"`로 설정하고 완료 개수 및 시간 지표는 모두 `"0"`으로 세팅하여 실제 클라이언트 에러 뷰가 감지할 수 있도록 응답을 구성합니다.
            4. Firebase 서비스를 통해 전송됩니다.
        """,
        security = [] // 로그인 토큰 요구 제외 (Swagger 자물쇠 풀림)
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "FCM 푸시 알림 발송 성공",
                content = [Content(schema = Schema(implementation = FcmTestResponse::class))]
            )
        ]
    )
    fun testRoutineFailure(
        @RequestBody request: FcmTestRoutineFailureRequest
    ): ResponseEntity<FcmTestResponse> {
        val totalCount = request.totalWeeks * 3
        val title = "루틴 생성 실패"
        val body = "루틴을 만드는 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요."
        val data = mapOf(
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

        fcmService.sendNotification(request.fcmToken, title, body, data)

        return ResponseEntity.ok(
            FcmTestResponse(
                status = "SUCCESS",
                message = "[$title] FCM 실패 알림 전송 성공!"
            )
        )
    }

    @PostMapping("/test/weekly-achievement")
    @Operation(
        summary = "FCM 실전 테스트: 주간 루틴 달성률 알림",
        description = """
            ### 🔓 [로그인 미필수 API - 자물쇠 해제]
            본 테스트 API는 로그인 인증 헤더(JWT Bearer)가 없어도 누구나 자유롭게 호출할 수 있습니다.
            
            ### ⚙️ [내부 동작 설명]
            1. 매주 일요일 정기 스케줄러(`RoutineNotificationScheduler`)가 계산하는 달성률 피드백 알림과 **동일하게 동작**합니다.
            2. 입력받은 `achievementRate`를 소수점 첫째 자리까지 포맷팅하여 푸시 알림 바디(`body`)를 조립합니다.
            3. `data` 맵에 `routineId`, `weekNumber`(주차), 날것의 `achievementRate`를 담아 보내어, 앱 클라이언트가 알림을 눌렀을 때 해당 주차 상세 페이지로 이동하거나 맞춤 연출을 수행하도록 가공해 줍니다.
        """,
        security = [] // 로그인 토큰 요구 제외 (Swagger 자물쇠 풀림)
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "FCM 푸시 알림 발송 성공",
                content = [Content(schema = Schema(implementation = FcmTestResponse::class))]
            )
        ]
    )
    fun testWeeklyAchievement(
        @RequestBody request: FcmTestWeeklyAchievementRequest
    ): ResponseEntity<FcmTestResponse> {
        val title = "${request.weekNumber}주차 루틴 달성률 알림"
        val body = "이번 주 루틴 달성률은 ${String.format("%.1f", request.achievementRate)}%입니다. 클릭해서 확인해 보세요!"
        val data = mapOf(
            "type" to "ROUTINE_WEEKLY_ACHIEVEMENT",
            "routineId" to request.routineId.toString(),
            "weekNumber" to request.weekNumber.toString(),
            "achievementRate" to request.achievementRate.toString()
        )

        fcmService.sendNotification(request.fcmToken, title, body, data)

        return ResponseEntity.ok(
            FcmTestResponse(
                status = "SUCCESS",
                message = "[$title] 달성률 ${request.achievementRate}% 알림 전송 성공!"
            )
        )
    }

    @PostMapping("/test/profile-reminder")
    @Operation(
        summary = "FCM 실전 테스트: 신체 정보 업데이트 알림",
        description = """
            ### 🔓 [로그인 미필수 API - 자물쇠 해제]
            본 테스트 API는 로그인 인증 헤더(JWT Bearer)가 없어도 누구나 자유롭게 호출할 수 있습니다.
            
            ### ⚙️ [내부 동작 설명]
            1. 매일 아침 프로필 방치 유저들에게 나가는 알림 스케줄러(`UserProfileReminderService`)의 알림 전송 로직을 테스트합니다.
            2. `data` 맵 없이 순수 시스템 알림 트레이 영역만 표출되는 특수 유형의 푸시가 실 기기에 올바르게 도착하는지 테스트할 수 있습니다.
        """,
        security = [] // 로그인 토큰 요구 제외 (Swagger 자물쇠 풀림)
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "FCM 푸시 알림 발송 성공",
                content = [Content(schema = Schema(implementation = FcmTestResponse::class))]
            )
        ]
    )
    fun testProfileReminder(
        @RequestBody request: FcmTestProfileReminderRequest
    ): ResponseEntity<FcmTestResponse> {
        val title = "신체 정보 업데이트 알림"
        val body = "키, 몸무게, 성별 정보를 최신 상태로 업데이트해주세요."

        fcmService.sendNotification(request.fcmToken, title, body)

        return ResponseEntity.ok(
            FcmTestResponse(
                status = "SUCCESS",
                message = "[$title] 알림 전송 성공!"
            )
        )
    }

    @PostMapping("/test/routine-completed")
    @Operation(
        summary = "FCM 실전 테스트: 루틴 생성 완료 알림 및 더미 데이터 반환",
        description = """
            ### 🔓 [로그인 미필수 API - 자물쇠 해제]
            본 테스트 API는 로그인 인증 헤더(JWT Bearer)가 없어도 누구나 자유롭게 호출할 수 있습니다.
            
            ### ⚙️ [내부 동작 설명]
            1. 입력받은 FCM 토큰으로 실제 루틴 생성 완료 알림과 동일한 페이로드(COMPLETED 상태 및 완료 데이터 맵 포함)의 푸시 알림을 즉시 발송합니다.
            2. 응답 본문으로 실제 루틴 생성이 완료되어 조회가 가능해졌을 때 클라이언트가 받아볼 수 있는 형태의 **1주차(3일 치) 루틴 더미 데이터**를 구성하여 즉시 반환합니다.
            3. 이를 통해 비동기  대기 없이 클라이언트 측의 푸시 알림 수신과 루틴 상세 화면 렌더링 연동을 안정적으로 테스트할 수 있습니다.
        """,
        security = [] // 로그인 토큰 요구 제외 (Swagger 자물쇠 풀림)
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "FCM 푸시 알림 발송 및 더미 데이터 반환 성공",
                content = [Content(schema = Schema(implementation = FcmRoutineCompletedTestResponse::class))]
            )
        ]
    )
    fun testRoutineCompleted(
        @RequestBody request: FcmTestRoutineCompletedRequest
    ): ResponseEntity<FcmRoutineCompletedTestResponse> {
        val title = "루틴 생성 완료!"
        val body = "${request.totalWeeks}주 동안의 맞춤 운동 루틴이 준비됐어요."
        
        val totalCount = request.totalWeeks * 3
        val data = mapOf(
            "type" to "ROUTINE_GENERATION",
            "status" to "COMPLETED",
            "phase" to "COMPLETED",
            "createdCount" to totalCount.toString(),
            "totalCount" to totalCount.toString(),
            "progressPercent" to "100",
            "estimatedFirstWeekMinutes" to "5",
            "estimatedFirstWeekRemainingMinutes" to "0",
            "estimatedTotalMinutes" to "8",
            "estimatedRemainingMinutes" to "0",
            "completed" to "true"
        )

        fcmService.sendNotification(request.fcmToken, title, body, data)

        // 1주차 3일치 더미 데이터 조립
        val date1 = request.startDate
        val date2 = request.startDate.plusDays(2)
        val date3 = request.startDate.plusDays(4)

        val dummyRoutine = listOf(
            RoutineDateResponse(
                date = date1,
                workouts = listOf(
                    RoutineWorkoutItemResponse(exerciseId = 101, sectionName = "Warm up", exerciseName = "제자리 걷기 March in place", repsTime = "5 min", completed = false),
                    RoutineWorkoutItemResponse(exerciseId = 102, sectionName = "Strength", exerciseName = "밴드 스쿼트 Band squat", repsTime = "12 reps", completed = false),
                    RoutineWorkoutItemResponse(exerciseId = 103, sectionName = "Cooldown", exerciseName = "스트레칭 Hamstring stretch", repsTime = "5 min", completed = false)
                )
            ),
            RoutineDateResponse(
                date = date2,
                workouts = listOf(
                    RoutineWorkoutItemResponse(exerciseId = 201, sectionName = "Warm up", exerciseName = "팔 돌리기 Arm circles", repsTime = "5 min", completed = false),
                    RoutineWorkoutItemResponse(exerciseId = 202, sectionName = "Strength", exerciseName = "벽 푸시업 Wall push up", repsTime = "12 reps", completed = false),
                    RoutineWorkoutItemResponse(exerciseId = 203, sectionName = "Cooldown", exerciseName = "종아리 스트레칭 Calf stretch", repsTime = "5 min", completed = false)
                )
            ),
            RoutineDateResponse(
                date = date3,
                workouts = listOf(
                    RoutineWorkoutItemResponse(exerciseId = 301, sectionName = "Warm up", exerciseName = "가벼운 조깅 Light jogging", repsTime = "10 min", completed = false),
                    RoutineWorkoutItemResponse(exerciseId = 302, sectionName = "Strength", exerciseName = "덤벨 숄더 프레스 Dumbbell shoulder press", repsTime = "10 reps", completed = false),
                    RoutineWorkoutItemResponse(exerciseId = 303, sectionName = "Cooldown", exerciseName = "전신 스트레칭 Full body stretch", repsTime = "5 min", completed = false)
                )
            )
        )

        return ResponseEntity.ok(
            FcmRoutineCompletedTestResponse(
                fcmStatus = "SUCCESS",
                fcmMessage = "[$title] FCM 푸시 전송 성공! (수신 기기의 트레이 및 앱 내 data 로그를 확인해 주세요)",
                dummyRoutine = dummyRoutine
            )
        )
    }
}

// ==========================================
// Swagger 테스트 요청 및 응답용 DTO 모델 클래스
// ==========================================

@Schema(description = "FCM 테스트 전송 결과 응답 데이터")
data class FcmTestResponse(
    @field:Schema(description = "처리 결과 상태", example = "SUCCESS")
    val status: String,
    
    @field:Schema(description = "결과 상세 메시지", example = "[루틴 생성 완료!] FCM 푸시 전송 성공!")
    val message: String
)

@Schema(description = "루틴 생성 알림 테스트 요청")
data class FcmTestRoutineProgressRequest(
    @field:Schema(description = "수신처 FCM 디바이스 토큰", example = "your-device-fcm-token")
    val fcmToken: String,
    
    @field:Schema(description = "생성 상태 (STARTED: 시작, GENERATING: 진행중, COMPLETED: 완료)", example = "COMPLETED")
    val status: String = "COMPLETED",
    
    @field:Schema(description = "생성 단계 (MULTI_WEEK_GENERATING, PERSISTING_WEEKS, COMPLETED)", example = "COMPLETED")
    val phase: String = "COMPLETED",
    
    @field:Schema(description = "진행률 계산을 위한 현재 주차 (GENERATING 일 때 반영)", example = "2")
    val week: Int = 2,
    
    @field:Schema(description = "목표 전체 주차 수", example = "4")
    val totalWeeks: Int = 4
)

@Schema(description = "루틴 생성 실패 알림 테스트 요청")
data class FcmTestRoutineFailureRequest(
    @field:Schema(description = "수신처 FCM 디바이스 토큰", example = "your-device-fcm-token")
    val fcmToken: String,
    
    @field:Schema(description = "목표 전체 주차 수", example = "4")
    val totalWeeks: Int = 4
)

@Schema(description = "주간 루틴 달성률 알림 테스트 요청")
data class FcmTestWeeklyAchievementRequest(
    @field:Schema(description = "수신처 FCM 디바이스 토큰", example = "your-device-fcm-token")
    val fcmToken: String,
    
    @field:Schema(description = "운동 루틴 DB ID", example = "42")
    val routineId: Long = 42,
    
    @field:Schema(description = "알림 대상 주차 번호", example = "3")
    val weekNumber: Int = 3,
    
    @field:Schema(description = "달성률 수치 (%)", example = "85.7")
    val achievementRate: Double = 85.7
)

@Schema(description = "신체 정보 업데이트 알림 테스트 요청")
data class FcmTestProfileReminderRequest(
    @field:Schema(description = "수신처 FCM 디바이스 토큰", example = "your-device-fcm-token")
    val fcmToken: String
)

@Schema(description = "루틴 생성 완료 알림 및 더미 데이터 테스트 요청")
data class FcmTestRoutineCompletedRequest(
    @field:Schema(description = "수신처 FCM 디바이스 토큰", example = "your-device-fcm-token")
    val fcmToken: String,
    
    @field:Schema(description = "목표 전체 주차 수 (기본값 4)", example = "4")
    val totalWeeks: Int = 4,

    @field:Schema(description = "더미 데이터 기준 시작 날짜 (기본값 오늘)", example = "2026-06-08")
    val startDate: LocalDate = LocalDate.now()
)

@Schema(description = "루틴 생성 완료 테스트 및 더미 데이터 응답")
data class FcmRoutineCompletedTestResponse(
    @field:Schema(description = "FCM 전송 결과 상태", example = "SUCCESS")
    val fcmStatus: String,
    
    @field:Schema(description = "FCM 결과 상세 메시지", example = "[루틴 생성 완료!] FCM 푸시 전송 성공!")
    val fcmMessage: String,

    @field:Schema(description = "완료된 것으로 가정하여 생성된 더미 루틴 데이터 목록 (1주차 분량)")
    val dummyRoutine: List<RoutineDateResponse>
)
