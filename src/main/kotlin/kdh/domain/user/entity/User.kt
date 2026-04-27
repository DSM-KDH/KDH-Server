package kdh.domain.user.entity

import jakarta.persistence.*

@Entity
@Table(name = "users", uniqueConstraints = [
    UniqueConstraint(columnNames = ["provider", "provider_id"])
])
class User(
    @Id
    @Column(name = "provider_id", nullable = false)
    val providerId: String,

    @Column(nullable = false)
    val provider: String,

    @Column(nullable = false)
    var name: String,
)
