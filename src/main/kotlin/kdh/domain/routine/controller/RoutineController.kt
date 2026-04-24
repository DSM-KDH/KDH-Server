package kdh.domain.routine.controller

import kdh.domain.routine.dto.RoutineCreateRequest
import kdh.domain.routine.service.RoutineService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/routines")
class RoutineController(
    private val routineService: RoutineService
) {

    @PostMapping
    fun createRoutine(@RequestBody request: RoutineCreateRequest): ResponseEntity<Void> {
        routineService.createRoutine(request)
        return ResponseEntity.ok().build()
    }
}
