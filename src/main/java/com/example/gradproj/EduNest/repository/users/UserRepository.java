package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.repository.users.projection.AuthUserProjection;
import com.example.gradproj.EduNest.repository.users.projection.MonthlyUsersProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserFullNameProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserListProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserNameProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserRoleProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
    r.name as roleName,
    u.deleted as deleted
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

    @Query("""
    select case when (u.enabled = true and u.deleted = false) then true else false end
    from UserEntity u
    where u.email = :email
""")
    Optional<Boolean> isUserEnabledAndNotDeleted(String email);

    @Query("""
        SELECT
            YEAR(u.createdAt) as year,
            MONTH(u.createdAt) as month,
            COUNT(u.id) as totalUsers
        FROM UserEntity u
        WHERE (:startDate IS NULL OR u.createdAt >= :startDate)
        GROUP BY YEAR(u.createdAt), MONTH(u.createdAt)
        ORDER BY YEAR(u.createdAt), MONTH(u.createdAt)
    """)
    List<MonthlyUsersProjection> getMonthlyUsersForLastPeriod(
            @Param("startDate") java.time.LocalDateTime startDate
    );

    @Query("""
        SELECT
            u.id as id,
            u.firstName as firstName,
            u.lastName as lastName,
            u.email as email,
            r.name as roleName,
            u.profileImageUrl as profileImageUrl,
            u.enabled as enabled
        FROM UserEntity u
        JOIN u.role r
        """)
    Page<UserListProjection> findAllUsers(Pageable pageable);

    @Query("""
        SELECT
            u.id as id,
            u.firstName as firstName,
            u.lastName as lastName,
            u.email as email,
            r.name as roleName,
            u.profileImageUrl as profileImageUrl,
            u.enabled as enabled
        FROM UserEntity u
        JOIN u.role r
        WHERE r.name = :roleName
        """)
    Page<UserListProjection> findUsersByRoleName(
            @Param("roleName") String roleName,
            Pageable pageable
    );

    @Query("""
        SELECT r.name as roleName
        FROM UserEntity u
        JOIN u.role r
        WHERE u.id = :userId
        """)
    Optional<UserRoleProjection> findRoleByUserId(@Param("userId") Long userId);

}
