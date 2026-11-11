package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MentorDistinctStudentsTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private EnrollmentRepository enrollments;

    @Test
    void countsPeopleRatherThanEnrollmentsAcrossMentorships() {
        var first = auth.mentorship("eb54-count-mentor@example.org", Status.ACTIVE);
        var second = auth.mentorship("eb54-count-mentor@example.org", Status.ACTIVE);
        assertThat(enrollments.countStudentsByMentorId(first.mentor().getId())).isZero();
        auth.enroll(first.mentorship().getId(), "eb54-count-a@example.org");
        auth.enroll(second.mentorship().getId(), "eb54-count-a@example.org");
        auth.enroll(second.mentorship().getId(), "eb54-count-b@example.org");

        assertThat(enrollments.countStudentsByMentorId(first.mentor().getId())).isEqualTo(2);
        assertThat(enrollments.countStudentsByMentorship(second.mentorship().getId())).isEqualTo(2);
    }
}
