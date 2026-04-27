package kdh

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KdhApplication

fun main(args: Array<String>) {
    runApplication<KdhApplication>(*args)
}
