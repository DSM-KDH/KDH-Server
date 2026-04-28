package kdh.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kdh.domain.user.enum.Gender
import java.time.LocalDateTime

@Entity
@Table(name = "user_profile_history")
class UserProfileHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_provider_id", referencedColumnName = "provider_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val heightCm: Double,

    @Column(nullable = false)
    val weightKg: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val gender: Gender,

    @Column(nullable = false)
    val recordedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var nextReminderAt: LocalDateTime = recordedAt.plusMonths(1)
)
