package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MentorShipRepository extends JpaRepository<MentorShip, Long> {
    boolean existsById(Long id);
    long countByMentor_Id(Long mentorId);

    @EntityGraph(attributePaths = {"tags"})
    Page<MentorShip> findAll(Pageable pageable);

}
