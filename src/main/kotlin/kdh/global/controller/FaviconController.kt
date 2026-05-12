package kdh.global.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FaviconController {
    @GetMapping("/favicon.ico")
    fun favicon(): ResponseEntity<Void> = ResponseEntity.noContent().build()
}
