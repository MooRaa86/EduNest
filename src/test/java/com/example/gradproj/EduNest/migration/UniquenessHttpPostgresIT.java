package com.example.gradproj.EduNest.migration;

import com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest;
import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.chat.ConversationRepository;
import com.example.gradproj.EduNest.repository.chat.MessageRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.chat.ConversationService;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipServiceI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-44 / [P3-02] — Conflict UX on the REAL stack: full Spring application
 * (production security chain + PostgreSQL) with
 * {@code spring.flyway.enabled=true} and {@code ddl-auto=validate}.
 *
 * Covers:
 * U1  double join via the real HTTP endpoint returns 409 CONFLICT with the
 *     {@code ERR_DUPLICATE_RESOURCE} envelope (409 path exercised end to end);
 * U2  concurrent joins on the same (student, mentorship) pair converge to
 *     exactly one enrollment (database constraint is authoritative);
 * U3  duplicate review via the HTTP endpoint returns 409 CONFLICT;
 * U4  two first-messages for the same user pair concurrently resolve to exactly
 *     one conversation which carries BOTH messages (ConstraintTemplate race
 *     recovery in ConversationService);
 * U5  sequential duplicate first-message still reuses one conversation.
 */
@Tag("critical")
@Tag("business-flow")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
public class UniquenessHttpPostgresIT {

	private static final Path UPLOAD_ROOT = createUploadRoot();

	/**
	 * Shared container is started eagerly (not via @Container JUnit lifecycle)
	 * because {@code @DynamicPropertySource} sources must be resolvable before
	 * the Spring context refreshes.
	 */
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

	private static Path createUploadRoot() {
		try {
			return Files.createTempDirectory("edunest-eb44-http-it-");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static {
		if (!DockerClientFactory.instance().isDockerAvailable()) {
			throw new IllegalStateException("Docker daemon is not available; EB-44 PostgreSQL HTTP IT cannot run");
		}
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		// the real PostgreSQL datasource overrides application-test's H2
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.flyway.enabled", () -> "true");
		// proves the entity baseline stays valid against the migrated schema
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("app.upload.base-dir", UPLOAD_ROOT::toString);
	}

	@Autowired private MockMvc mockMvc;
	@Autowired private AuthFixture auth;
	@Autowired private EnrollmentRepository enrollments;
	@Autowired private ReviewsRepository reviews;
	@Autowired private ConversationRepository conversations;
	@Autowired private MessageRepository messages;
	@Autowired private ConversationService conversationService;
	@Autowired private mentorShipServiceI mentorShipService;
	@Autowired private com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository commissions;

	// the commission config row join/rate flows depend on (fresh PG database starts empty)
	@org.junit.jupiter.api.BeforeEach
	void seedCommissionBaseline() {
		if (commissions.count() == 0) {
			commissions.saveAndFlush(com.example.gradproj.EduNest.entity.mentorship.Commission.builder()
					.rate(10.0)
					.build());
		}
	}

	@Test
	@Tag("critical")
	void u1_duplicateJoinReturns409DuplicateResource() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "eb44-u1-mentor-" + unique + "@example.org";
		String studentEmail = "eb44-u1-student-" + unique + "@example.org";

		auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();
		String token = auth.authToken(studentEmail, AuthFixture.STUDENT);

		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipId)
						.header("Authorization", token))
				.andExpect(status().isOk());

		// second join hits uk_enrollment_student_mentorship -> 409 envelope
		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipId)
						.header("Authorization", token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_RESOURCE"))
				.andExpect(jsonPath("$.message").value("You are already enrolled in this mentorship"));

		assertThat(enrollments.findAll().stream()
				.filter(e -> e.getMentorShip().getId().equals(mentorshipId))
				.count())
				.as("Exactly one enrollment row must exist")
				.isEqualTo(1);
	}

	@Test
	@Tag("critical")
	void u2_concurrentJoinsConvergeToOneEnrollment() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "eb44-u2-mentor-" + unique + "@example.org";
		String studentEmail = "eb44-u2-student-" + unique + "@example.org";

		auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();

		final List<Object> outcomes = runRacing(() -> {
			mentorShipService.joinMentorship(mentorshipId);
			return null;
		}, studentEmail);

		assertThat(outcomes)
				.as("One racing join must commit and one must be translated into DuplicateResourceException (409)")
				.containsNull()
				.hasAtLeastOneElementOfType(com.example.gradproj.EduNest.exception.DuplicateResourceException.class);

		long enrollmentCount = enrollments.findAll().stream()
				.filter(e -> e.getMentorShip().getId().equals(mentorshipId))
				.count();
		assertThat(enrollmentCount)
				.as("The (student, mentorship) pair must produce exactly one enrollment row")
				.isEqualTo(1);
	}

	private List<Object> runRacing(Callable<Object> task, String actingEmail) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CyclicBarrier barrier = new CyclicBarrier(2);
			Callable<Object> wrapped = () -> {
				barrier.await(5, TimeUnit.SECONDS);
				SecurityContextHolder.setContext(new SecurityContextImpl(
						new UsernamePasswordAuthenticationToken(actingEmail, null, List.of())));
				try {
					return task.call();
				} catch (Exception e) {
					return e;
				} finally {
					SecurityContextHolder.clearContext();
				}
			};
			java.util.concurrent.Future<Object> f1 = pool.submit(wrapped);
			java.util.concurrent.Future<Object> f2 = pool.submit(wrapped);
			List<Object> collected = new java.util.ArrayList<>();
			collected.add(f1.get(20, TimeUnit.SECONDS));
			collected.add(f2.get(20, TimeUnit.SECONDS));
			return collected;
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@Tag("critical")
	void u3_concurrentDuplicateReviewsResolveToOneRowWith409Translation() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "eb44-u3-mentor-" + unique + "@example.org";
		String studentEmail = "eb44-u3-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();
		auth.enroll(mentorshipId, studentEmail);

		final List<Object> outcomes = runRacing(() -> {
			mentorShipService.rateMentorship(mentorshipId,
					new com.example.gradproj.EduNest.dto.mentorShipDTOs.request.CreateReviewRequest() {{
						setRating(5);
						setFeedback("great");
					}});
			return null;
		}, studentEmail);

		assertThat(outcomes)
				.as("Exactly one review tx must commit (null) and the racing one must be translated to DuplicateResourceException (409)")
				.containsNull()
				.hasAtLeastOneElementOfType(com.example.gradproj.EduNest.exception.DuplicateResourceException.class);

		List<MentorShipReviews> pairReviews = reviews.findAll().stream()
				.filter(r -> mentorshipId.equals(r.getMentorShip().getId()))
				.filter(r -> student.getId().equals(r.getStudent().getId()))
				.toList();
		assertThat(pairReviews)
				.as("The (student, mentorship) review pair must produce exactly one surviving row")
				.hasSize(1);
	}

	@Test
	@Tag("critical")
	void u4_concurrentFirstMessagesResolveToOneConversation() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String emailA = "eb44-u4-a-" + unique + "@example.org";
		String emailB = "eb44-u4-b-" + unique + "@example.org";
		auth.student(emailA);
		auth.student(emailB);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CyclicBarrier barrier = new CyclicBarrier(2);
			Callable<Object> sendMessage = () -> {
				barrier.await(5, TimeUnit.SECONDS);
				return conversationService.sendMessage(emailA,
						ConversationMessageRequest.builder()
								.recipientEmail(emailB)
								.content("racing first message")
								.build());
			};
			java.util.concurrent.Future<Object> f1 = pool.submit(sendMessage);
			java.util.concurrent.Future<Object> f2 = pool.submit(sendMessage);
			List<Object> sent = new java.util.ArrayList<>();
			sent.add(f1.get(20, TimeUnit.SECONDS));
			sent.add(f2.get(20, TimeUnit.SECONDS));

			sent.forEach(s -> assertThat(s)
					.as("Both racing sends must succeed (constraint-race recovery), not throw")
					.isNotInstanceOf(Throwable.class));

			long pairConversations = conversations.findAll().stream()
					.filter(c -> c.getUser1() != null && c.getUser2() != null)
					.filter(c -> {
						String one = c.getUser1().getEmail();
						String two = c.getUser2().getEmail();
						return (one.equals(emailA) && two.equals(emailB)) || (one.equals(emailB) && two.equals(emailA));
					})
					.count();
			assertThat(pairConversations)
					.as("Exactly one conversation must exist for the user pair")
					.isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}

		assertThat(messages.findAll().stream()
				.filter(m -> m.getSender().getEmail().equals(emailA))
				.count())
				.as("Both racing messages must be persisted on the surviving conversation")
				.isEqualTo(2);
	}

	@Test
	@Tag("critical")
	void u5_sequentialDuplicateFirstMessageReusesOneConversation() {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String emailA = "eb44-u5-a-" + unique + "@example.org";
		String emailB = "eb44-u5-b-" + unique + "@example.org";
		auth.student(emailA);
		auth.student(emailB);

		conversationService.sendMessage(emailA, ConversationMessageRequest.builder()
				.recipientEmail(emailB).content("one").build());
		conversationService.sendMessage(emailA, ConversationMessageRequest.builder()
				.recipientEmail(emailB).content("two").build());

		long pairConversations = conversations.findAll().stream()
				.filter(c -> c.getUser1() != null && c.getUser2() != null)
				.filter(c -> c.getUser1().getEmail().equals(emailA) && c.getUser2().getEmail().equals(emailB))
				.count();
		assertThat(pairConversations).isEqualTo(1);

		assertThat(messages.findAll().stream()
				.filter(m -> m.getSender().getEmail().equals(emailA))
				.filter(m -> m.getConversation().getUser2().getEmail().equals(emailB))
				.count())
				.as("Both sequential messages must land on the same conversation")
				.isEqualTo(2);
	}
}
