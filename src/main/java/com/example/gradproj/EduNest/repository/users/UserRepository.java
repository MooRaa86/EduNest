package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.repository.users.projection.AuthUserProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserFullNameProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserNameProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndEnabledTrue(String email);

    @Query("""
    select
        u.id as id,
        u.firstName as firstName,
        u.lastName as lastName,
        u.email as email
    from UserEntity u
    where u.email = :email
""")
    Optional<UserNameProjection> findSenderInfo(String email);

    @Query("""
    select
        u.email as email,
        concat(u.firstName, ' ', u.lastName) as fullName
    from UserEntity u
    where u.email = :email
""")
    Optional<UserFullNameProjection> findFullNameByEmail(String email);


    @Query("select u.id from UserEntity u where u.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);

    @Query("""
select
    u.id as id,
    u.email as email,
    u.password as password,
    u.enabled as enabled,
    r.name as roleName
from UserEntity u
join u.role r
where u.email = :email
""")
    Optional<AuthUserProjection> findAuthUser(String email);

    @Query("""
    select u.enabled
    from UserEntity u
    where u.email = :email
""")
    Optional<Boolean> isUserEnabled(String email);



}
