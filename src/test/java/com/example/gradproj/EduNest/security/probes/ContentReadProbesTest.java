package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-03 / EB-37 — G4/G5/G6: content reads across mentorship boundaries.
 * Lesson material / tasks / projects of a mentorship the caller is NOT enrolled in.
 */
@Tag("critical")
class ContentReadProbesTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private MentorShipRepository mentorships;
	@Autowired private WeekRepository weeks;
	@Autowired private LectureRepository lectures;
	@Autowired private TaskRepository tasks;
	@Autowired private ProjectRepository projects;

	private static final String STRANGER = "probe-content-stranger@example.org";
	private Long mentorshipId;
	private Long lectureId;
	private Long taskId;
	private Long projectId;

	@BeforeEach
	void seedContentChain() {
		var chain = auth.mentorship("probe-content-mentor@example.org",
				com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
		mentorshipId = chain.mentorship().getId();

		Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("probe week").build());

		lectureId = lectures.saveAndFlush(Lecture.builder()
				.title("probe lecture").lectureUrl("https://example.org/secret-media").week(week).build())
				.getId();

		taskId = tasks.saveAndFlush(Task.builder().title("probe task")
				.description("probe-task-doc")
				.status(com.example.gradproj.EduNest.enums.tasks.TaskStatus.DRAFT)
				.week(week).points(10).passPoints(5).estimatedMinutes(30)
				.dueAt(java.time.LocalDateTime.now().plusDays(1)).build()).getId();

		projectId = projects.saveAndFlush(Project.builder().title("probe project")
				.brief("probe-brief").goal("probe-goal")
				.status(com.example.gradproj.EduNest.enums.project.ProjectStatus.DRAFT)
				.startAt(java.time.LocalDateTime.now())
				.endAt(java.time.LocalDateTime.now().plusDays(7))
				.week(week).points(10).build()).getId();

		auth.student(STRANGER); // un-enrolled foreigner in another account exists
	}

	/**
	 * G4 — lecture reads (3 endpoints) expose media URLs to any authenticated user.
	 * Audit: LectureController has NO ownership annotations; service has no enrollment gate.
	 * EXPECT TODAY: 200 on all three read surfaces.
	 * FLIPS WITH: [P1-14] content-read guards (EB-21) → expect 403 then.
	 */
	@Test
	@DisplayName("G4/todayOpen: stranger reads a foreign lecture by id → 200")
	void g4_strangerReadsLectureById() throws Exception {
		mockMvc.perform(get("/lectures/{id}", lectureId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("G4b/todayOpen: stranger reads a foreign mentorship's lectures by mentorship → 200")
	void g4b_strangerReadsLecturesByMentorship() throws Exception {
		mockMvc.perform(get("/lectures/mentorship/{mentorshipId}", mentorshipId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("G4c/todayOpen: stranger reads lectures by foreign week → 200")
	void g4c_strangerReadsLecturesByWeek() throws Exception {
		Long weekId = weeks.findAll().stream()
				.filter(w -> w.getMentorship() != null && mentorshipId.equals(w.getMentorship().getId()))
				.mapToLong(x -> x.getId()).findFirst().orElseThrow();
		mockMvc.perform(get("/lectures/week/{weekId}", weekId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	/**
	 * G5 — task list incl. DRAFT content and attachment paths.
	 * Audit: mentorShipServiceI.getMentorShipTasks only checks existsById.
	 * EXPECT TODAY: 200.
	 * FLIPS WITH: [P1-14] (EB-21).
	 */
	@Test
	@DisplayName("G5/todayOpen: stranger lists a foreign mentorship's tasks → 200")
	void g5_strangerListsForeignTasks() throws Exception {
		mockMvc.perform(get("/api/v1/mentorship/{mid}/get-tasks", mentorshipId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	/**
	 * G6 — task/project detail + mentorship-filtered lists for a foreigner.
	 * EXPECT TODAY: 200 on all surfaces.
	 * FLIPS WITH: [P1-14] (EB-21).
	 */
	@Test
	@DisplayName("G6/todayOpen: stranger reads a foreign task by id → 200")
	void g6_strangerReadsTaskById() throws Exception {
		mockMvc.perform(get("/api/v1/task/{id}", taskId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("G6b/todayOpen: stranger reads a foreign project by id → 200")
	void g6b_strangerReadsProjectById() throws Exception {
		mockMvc.perform(get("/api/v1/project/{id}", projectId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("G6c/todayOpen: stranger lists tasks by foreign mentorship filter → 200")
	void g6c_strangerListsTasksByMentorshipFilter() throws Exception {
		mockMvc.perform(get("/api/v1/task/filter/{msid}", mentorshipId)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isOk());
	}
}
