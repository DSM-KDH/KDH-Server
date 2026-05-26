package kdh.global.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
class FaviconController {
    @GetMapping("/favicon.ico")
    fun favicon(): ResponseEntity<Void> = ResponseEntity.noContent().build()
}
