package kdh.domain.user.entity

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @Column(nullable = false, unique = true)
    val providerId: String,

    @Column(nullable = false)
    var name: String,
)
