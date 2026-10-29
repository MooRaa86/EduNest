package com.example.gradproj.EduNest.security.probes;

import com.jayway.jsonpath.JsonPath;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * P1-21 / EB-28 — Upload exposure policy regression probes (FILE-01, TD-09/G14, D-07).
 *
 * Policy under test:
 *  - public categories (mentorship-cover-images, student, mentor, chatroom):
 *    anonymously fetchable via /uploads/{public}/** — unchanged public URLs.
 *  - private categories (submissions, task-attachment, project-attachment):
 *    NEVER served via /uploads/** — not even for AUTHENTICATED users. Access only
 *    through ownership-checked typed /files/** endpoints.
 *  - admin + unknown categories: fail-closed (non-public by default until a
 *    documented FE contract demands otherwise).
 *  - BOTH layers enforce this: the prod security chain AND the MVC resource
 *    handlers (no broad /* fallback, no file:uploads/ static-locations leak).
 *
 * Fixtures are synthetic files under a newly-created temporary test base dir
 * — the repository's real
 * uploads/ tree and user files are never touched. Each file is deleted after
 * its test (directories are left; deleting them races readers).
 */
@Tag("critical")
class UploadExposurePolicyProbeTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private WeekRepository weeks;
	@Autowired private TaskRepository tasks;
	@Autowired private TaskSubmissionRepository submissions;
	@Autowired private WebApplicationContext webContext;
	@Autowired private ImageStorageService images;
	@Autowired private Environment environment;

	@Value("${app.upload.base-dir}") private String baseDir;

	private static final String STUDENT = "upload-policy-student@example.org";
	private static final String STRANGER = "upload-policy-stranger@example.org";
	private static final String MENTOR = "upload-policy-mentor@example.org";

	private final List<Path> writtenFiles = new ArrayList<>();

	@BeforeEach
	void seed() {
		auth.student(STUDENT);
		auth.student(STRANGER);
	}
	@AfterEach
	void cleanup() {
		for (Path file : writtenFiles) {
			try {
				Files.deleteIfExists(file);
			} catch (Exception ignored) {
				// best effort — unique names keep collisions impossible anyway
			}
		}
		writtenFiles.clear();
	}

	/** Writes a synthetic fixture file under the disposable test base dir. */
	private String fixture(String folder, String fileName) throws Exception {
		Path dir = Path.of(baseDir).resolve(folder);
		Files.createDirectories(dir);
		Path file = dir.resolve(fileName);
		Files.write(file, ("eb28 fixture " + fileName).getBytes(StandardCharsets.UTF_8));
		writtenFiles.add(file);
		return folder + "/" + fileName;
	}

	/** A unique fixture suffix for this test process. */
	private static final String UNIQUE_SUFFIX = "eb28-" + System.nanoTime();

	// ---------- private categories: anonymous blocked (security chain, 401) ----------

	@ParameterizedTest
	@ValueSource(strings = {"submissions", "task-attachment", "project-attachment"})
	@DisplayName("P1-21/FILE-01: anonymous GET /uploads/{private}/{file} → 401 (was 200 via catch-all)")
	void privateCategory_anonymousBlocked(String folder) throws Exception {
		fixture(folder, "private-" + UNIQUE_SUFFIX + ".zip");

		mockMvc.perform(get("/uploads/" + folder + "/private-" + UNIQUE_SUFFIX + ".zip"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- private categories: even AUTHENTICATED users are denied ----------

	@ParameterizedTest
	@ValueSource(strings = {"submissions", "task-attachment", "project-attachment"})
	void privateCategory_authenticatedStudentStillDenied(String folder) throws Exception {
		fixture(folder, "private-" + UNIQUE_SUFFIX + ".zip");

		mockMvc.perform(get("/uploads/" + folder + "/private-" + UNIQUE_SUFFIX + ".zip")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-21/FILE-01: AUTHENTICATED mentor GET /uploads/task-attachment/… → 403")
	void privateCategory_authenticatedMentorStillDenied() throws Exception {
		auth.mentor(MENTOR);
		fixture("task-attachment", "private-" + UNIQUE_SUFFIX + ".zip");

		mockMvc.perform(get("/uploads/task-attachment/private-" + UNIQUE_SUFFIX + ".zip")
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isForbidden());
	}

	// ---------- public categories: anonymous 200 (URL contract preserved) ----------

	@ParameterizedTest
	@ValueSource(strings = {"mentorship-cover-images", "student", "mentor", "chatroom"})
	@DisplayName("P1-21/D-07: anonymous GET /uploads/{public}/{file} → 200 (public URL contract kept)")
	void publicCategory_anonymousAllowed(String folder) throws Exception {
		fixture(folder, "public-" + UNIQUE_SUFFIX + ".jpg");

		mockMvc.perform(get("/uploads/" + folder + "/public-" + UNIQUE_SUFFIX + ".jpg"))
				.andExpect(status().isOk())
				.andExpect(content().bytes(("eb28 fixture public-" + UNIQUE_SUFFIX + ".jpg").getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void configuredRootIsSharedByImageWritesAndPublicReads() throws Exception {
		byte[] bytes = "synthetic image bytes".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", bytes);
		String cover = images.saveCoverImage(901L, image);
		String student = images.saveImage("student", 902L, image);
		for (String url : List.of(cover, student)) {
			Path saved = Path.of(baseDir).resolve(url.substring("/uploads/".length()));
			writtenFiles.add(saved);
			assertThat(Files.readAllBytes(saved)).isEqualTo(bytes);
			mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
		}
		images.deleteOldCoverImage(cover);
		images.deleteImage("student", student);
		for (Path file : writtenFiles) assertThat(Files.exists(file)).isFalse();
	}

	@Test
	void productionStaticLocationsDoNotExposeUploadRoot() throws Exception {
		assertThat(environment.getProperty("spring.web.resources.static-locations"))
				.isEqualTo("classpath:/static/");
		String name = "unprefixed-" + UNIQUE_SUFFIX + ".zip";
		fixture("submissions", name);
		var mvc = webAppContextSetup(webContext).build();
		var response = mvc.perform(get("/submissions/" + name)).andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(response.getContentAsString(), "$.errorCode")).isEqualTo("ERR_RESOURCE_NOT_FOUND");
		assertThat(response.getContentAsString())
				.doesNotContain("eb28 fixture " + name);
	}

	@Test
	void mvcHandlersIndependentlyServeOnlyPublicFolders() throws Exception {
		var mvcWithoutSecurityFilters = webAppContextSetup(webContext).build();
		String name = "mvc-" + UNIQUE_SUFFIX + ".jpg";
		fixture("student", name);
		mvcWithoutSecurityFilters.perform(get("/uploads/student/" + name))
				.andExpect(status().isOk());
		for (String folder : List.of("submissions", "task-attachment", "project-attachment", "admin", "unknown")) {
			fixture(folder, name);
			int status = mvcWithoutSecurityFilters.perform(get("/uploads/" + folder + "/" + name))
					.andReturn().getResponse().getStatus();
			assertThat(status).as("MVC must not serve %s", folder).isEqualTo(404);
		}
	}

	// ---------- admin + unknown categories: fail-closed (non-public default) ----------

	@Test
	@DisplayName("P1-21: anonymous GET /uploads/admin/… → 401 (admin avatars are not public by default)")
	void adminCategory_anonymousNotPublic() throws Exception {
		fixture("admin", "avatar-" + UNIQUE_SUFFIX + ".jpg");

		mockMvc.perform(get("/uploads/admin/avatar-" + UNIQUE_SUFFIX + ".jpg"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("P1-21: AUTHENTICATED student GET /uploads/admin/… → 403 (admin content is not for /uploads/**)")
	void adminCategory_authenticatedAlsoDenied() throws Exception {
		fixture("admin", "avatar-" + UNIQUE_SUFFIX + ".jpg");

		mockMvc.perform(get("/uploads/admin/avatar-" + UNIQUE_SUFFIX + ".jpg")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminImageIsAlsoDeniedToAdmin() throws Exception {
		String admin = "upload-policy-admin@example.org";
		auth.admin(admin);
		fixture("admin", "admin-" + UNIQUE_SUFFIX + ".jpg");
		mockMvc.perform(get("/uploads/admin/admin-" + UNIQUE_SUFFIX + ".jpg")
				.header("Authorization", auth.authToken(admin, AuthFixture.ADMIN)))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"submissions", "task-attachment", "project-attachment", "student"})
	void genericEndpointDeniesExistingFiles(String folder) throws Exception {
		String path = fixture(folder, "legacy-" + UNIQUE_SUFFIX + ".txt");
		mockMvc.perform(get("/files").param("filePath", path)
				.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("P1-21 fail-closed: unknown category /uploads/widgets/… → 401 anonymous / 403 authenticated")
	void unknownCategory_failClosed() throws Exception {
		fixture("widgets", "unknown-" + UNIQUE_SUFFIX + ".bin");

		mockMvc.perform(get("/uploads/widgets/unknown-" + UNIQUE_SUFFIX + ".bin"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/uploads/widgets/unknown-" + UNIQUE_SUFFIX + ".bin")
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isForbidden());
	}

	// ---------- traversal-like / encoded variants: never 200 ----------

	@Test
	@DisplayName("P1-21: traversal into a private folder via a public prefix → never 200")
	void traversalPublicPrefixIntoPrivate_neverServed() throws Exception {
		fixture("submissions", "deep-" + UNIQUE_SUFFIX + ".zip");

		MvcResult result = mockMvc.perform(get("/uploads/student/../submissions/deep-" + UNIQUE_SUFFIX + ".zip"))
				.andReturn();
		assertThat(result.getResponse().getStatus())
				.as("traversal through public prefix must be stopped by firewall or chain, got %s",
						result.getResponse().getStatus())
				.isIn(400, 401, 403);
	}

	@Test
	@DisplayName("P1-21: encoded traversal %2e%2e hiding a private folder → never 200")
	void encodedTraversal_neverServed() throws Exception {
		fixture("submissions", "enc-" + UNIQUE_SUFFIX + ".zip");

		MvcResult result = mockMvc.perform(get(URI.create("/uploads/student/%2e%2e%2fsubmissions/enc-" + UNIQUE_SUFFIX + ".zip")))
				.andReturn();
		assertThat(result.getResponse().getStatus())
				.as("encoded traversal must not serve the file, got %s", result.getResponse().getStatus())
				.isEqualTo(400);
	}

	@Test
	@DisplayName("P1-21: double-slash variants of a private path → never 200 (firewall rejects)")
	void doubleSlashVariants_neverServed() throws Exception {
		fixture("submissions", "ds-" + UNIQUE_SUFFIX + ".zip");

		for (String url : List.of(
				"//uploads/submissions/ds-" + UNIQUE_SUFFIX + ".zip",
				"/uploads//submissions/ds-" + UNIQUE_SUFFIX + ".zip")) {
			MvcResult result = mockMvc.perform(get(url)).andReturn();
			assertThat(result.getResponse().getStatus())
					.as(url + " must not serve, got %s", result.getResponse().getStatus())
					.isBetween(400, 499);
		}
	}

	// ---------- typed /files/** routes: ownership-checked serving still works ----------

	@Test
	@DisplayName("P1-21: authorized owner via /files/task-submissions/{id} → 200")
	void typedRoute_ownerAuthorized() throws Exception {
		String filePath = fixture("submissions", "typed-" + UNIQUE_SUFFIX + ".zip");
		long id = seedSubmissionChain(STUDENT, MENTOR, filePath);

		mockMvc.perform(get("/files/task-submissions/" + id)
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isOk())
				.andExpect(content().bytes(("eb28 fixture typed-" + UNIQUE_SUFFIX + ".zip").getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	@DisplayName("P1-21: the owning mentor is authorized via typed route → 200")
	void typedRoute_mentorAuthorized() throws Exception {
		String filePath = fixture("submissions", "typed-mentor-" + UNIQUE_SUFFIX + ".zip");
		long id = seedSubmissionChain(STUDENT, MENTOR, filePath);

		mockMvc.perform(get("/files/task-submissions/" + id)
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("P1-21: stranger student via typed route → denied, file never served")
	void typedRoute_strangerForbidden() throws Exception {
		String filePath = fixture("submissions", "typed-stranger-" + UNIQUE_SUFFIX + ".zip");
		long id = seedSubmissionChain(STUDENT, MENTOR, filePath);

		// Ownership check denies the stranger with the unified sanitized envelope.
		MvcResult result = mockMvc.perform(get("/files/task-submissions/" + id)
						.header("Authorization", auth.authToken(STRANGER, AuthFixture.STUDENT)))
				.andReturn();
		assertThat(result.getResponse().getStatus())
				.as("stranger must be denied, got %s",
						result.getResponse().getStatus())
				.isEqualTo(403);
		assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.errorCode")).isEqualTo("ERR_FORBIDDEN");
		assertThat(result.getResponse().getContentAsString())
				.doesNotContain("eb28 fixture typed-stranger-");
	}

	@Test
	@DisplayName("P1-21: anonymous via typed route → 401")
	void typedRoute_anonymousUnauthorized() throws Exception {
		String filePath = fixture("submissions", "typed-anon-" + UNIQUE_SUFFIX + ".zip");
		long id = seedSubmissionChain(STUDENT, MENTOR, filePath);

		mockMvc.perform(get("/files/task-submissions/" + id))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * Seeds mentorship → week → task → submission with the fixture path as
	 * uploadedFilePath relative to the isolated upload root.
	 */
	private long seedSubmissionChain(String studentEmail, String mentorEmail, String filePath) {
		MentorShip ms = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE).mentorship();
		Week week = weeks.saveAndFlush(Week.builder().mentorship(ms).title("EB-28 probe week").build());
		Task task = tasks.saveAndFlush(Task.builder()
				.week(week)
				.title("EB-28 probe task")
				.description("EB-28 probe task description")
				.points(100).passPoints(50).estimatedMinutes(30)
				.dueAt(LocalDateTime.now().plusDays(1))
				.status(TaskStatus.PUBLISHED)
				.build());
		TaskSubmission submission = submissions.saveAndFlush(TaskSubmission.builder()
				.task(task)
				.student(auth.student(studentEmail))
				.uploadedFilePath(filePath)
				.submittedAt(LocalDateTime.now())
				.build());
		return submission.getId();
	}
}
