package kdh.domain.user.entity

import jakarta.persistence.*

@Entity
@Table(name = "users", uniqueConstraints = [
    UniqueConstraint(columnNames = ["provider", "providerId"])
])
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val provider: String,

    @Column(nullable = false)
    val providerId: String,

    @Column(nullable = false)
    var name: String,
)
