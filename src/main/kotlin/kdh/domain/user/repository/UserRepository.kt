package kdh.domain.user.repository

import kdh.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: String, providerId: String): User?
    fun deleteByProviderAndProviderId(provider: String, providerId: String)
}
