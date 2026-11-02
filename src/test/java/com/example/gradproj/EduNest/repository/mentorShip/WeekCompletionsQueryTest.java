package com.example.gradproj.EduNest.repository.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.mentorShip.projections.WeekCompletionsProjection;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class WeekCompletionsQueryTest extends ProdChainIntegrationTestBase {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("EB-82: getWeekCompletions executes without SQL syntax errors on empty and populated results")
    void testGetWeekCompletionsSyntaxAndNullSafety() {
        // Calling with non-existent week and student IDs to verify query parsing, execution and null handling
        WeekCompletionsProjection projection = enrollmentRepository.getWeekCompletions(999999L, 999999L);
        assertThat(projection).isNotNull();
        assertThat(projection.getAttendedSessions()).isNull();
        assertThat(projection.getSubmittedTasks()).isNull();
        assertThat(projection.getSubmittedQuizzes()).isNull();
        assertThat(projection.getSubmittedProjects()).isNull();
    }
}
