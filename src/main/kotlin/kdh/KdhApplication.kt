package kdh

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KdhApplication

fun main(args: Array<String>) {
    println("### DB_URL: " + System.getenv("DB_URL"))
    println("### DB_USERNAME: " + System.getenv("DB_USERNAME"))
    println("### DB_PASSWORD: " + System.getenv("DB_PASSWORD"))
    runApplication<KdhApplication>(*args)
}
