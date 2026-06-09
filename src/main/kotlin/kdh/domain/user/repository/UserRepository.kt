package kdh.domain.user.repository

import kdh.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByProviderAndProviderId(provider: String, providerId: String): User?

    @Modifying
    @Query("delete from User u where u.provider = :provider and u.providerId = :providerId")
    fun deleteByProviderAndProviderId(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    )
}
