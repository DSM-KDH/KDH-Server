package kdh

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KdhApplication

fun main(args: Array<String>) {
    runApplication<KdhApplication>(*args)
}
