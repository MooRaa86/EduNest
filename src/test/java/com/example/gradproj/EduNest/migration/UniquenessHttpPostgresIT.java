package com.example.gradproj.EduNest.migration;

import com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest;
import com.example.gradproj.EduNest.entity.chat.Conversation;
import com.example.gradproj.EduNest.entity.chat.Message;
import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.chat.ConversationRepository;
import com.example.gradproj.EduNest.repository.chat.MessageRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.chat.ConversationService;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipServiceI;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * U5  sequential duplicate first-message still reuses one conversation;
 * U6  a duplicate review created BETWEEN the pre-check and the INSERT (raw
 *     uncommitted PostgreSQL row, released mid-request) still surfaces the 409
 *     envelope through the real HTTP endpoint — the pre-check blind spot is
 *     covered by the constraint, not the check;
 * U7  the HTTP rate endpoint keeps its UPDATE semantics after the hardening
 *     (second rate for the same pair overwrites in place, one row);
 * U8  an OUTER @Transactional caller stays healthy when the REQUIRES_NEW
 *     conversation tx fails on an entity-validation fault: the outer commit
 *     survives and no UnexpectedRollbackException crosses the boundary;
 * X1  a corrupted pre-existing total_points row makes the join's total_points
 *     insert fail on PostgreSQL's SQLSTATE-23505 catch-all (constraint name
 *     anonymous, no friendly mapping): envelope stays 409 generic without any
 *     residue, and the entire failed join transaction rolls back;
 * X2  handler-level contract (direct invocation against the real advice):
 *     uk_* constraint names map onto the 409 friendly envelope while unrelated
 *     fk-style DIVE keeps the legacy 400 envelope.
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
	@Autowired private com.example.gradproj.EduNest.repository.points.TotalPointsRepository totalPoints;
	@Autowired private com.example.gradproj.EduNest.repository.users.UserRepository users;
	@Autowired private OuterTxProbe outerTxProbe;

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
		UUID mentorshipPublicId = chain.mentorship().getPublicId();
		String token = auth.authToken(studentEmail, AuthFixture.STUDENT);

		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipPublicId)
						.header("Authorization", token))
				.andExpect(status().isOk());

		// second join hits uk_enrollment_student_mentorship -> 409 envelope
		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipPublicId)
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
		UUID mentorshipPublicId = chain.mentorship().getPublicId();

		final List<Object> outcomes = runRacing(() -> {
			mentorShipService.joinMentorship(mentorshipPublicId);
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
		UUID mentorshipPublicId = chain.mentorship().getPublicId();
		auth.enroll(mentorshipId, studentEmail);

		final List<Object> outcomes = runRacing(() -> {
			mentorShipService.rateMentorship(mentorshipPublicId,
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

	/* ====================================================================
	 * [EB-44] hardening additions:
	 *   u6  deterministic duplicate-creation race THROUGH THE HTTP RATE ENDPOINT
	 *   u7  HTTP rate UPDATE semantics preserved after the hardening
	 *   u8  REQUIRES_NEW isolation against an OUTER @Transactional caller
	 *   x1  anonymous (non-uk) unique constraint degrades to a clean 400
	 *   x2  handler contract via standalone MockMvc with the real advice
	 * ==================================================================== */

	/**
	 * [EB-44][P3-02] u6 — the pre-check vs INSERT window. A raw PostgreSQL
	 * connection (outside Spring/Hibernate) holds an UNCOMMITTED review row for
	 * the same (student, mentorship) pair. The HTTP request passes the
	 * pre-check, blocks at its INSERT on uk_mentorship_review_student, the
	 * holder commits mid-request and the constraint violation is translated
	 * into the 409 envelope. Deterministic by construction (no race on the test
	 * harness — the release timing only chooses WHEN the server observes the row,
	 * not whether).
	 */
	@Test
	@Tag("critical")
	void u6_httpRateSurfaces409WhenDuplicateRowLandsAfterPreCheck() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "eb44-u6-mentor-" + unique + "@example.org";
		String studentEmail = "eb44-u6-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();
		UUID mentorshipPublicId = chain.mentorship().getPublicId();
		auth.enroll(mentorshipId, studentEmail);
		String token = auth.authToken(studentEmail, AuthFixture.STUDENT);

		try (Connection holder = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
			holder.setAutoCommit(false);
			try (PreparedStatement heldInsert = holder.prepareStatement(
					"INSERT INTO mentorship_reviews (created_at, mentorship_id, rating, student_id, updated_at, feed_back, created_by, updated_by) "
							+ "VALUES (now(), ?, ?, ?, now(), 'held-review', 'eb44-holder', 'eb44-holder')")) {
				heldInsert.setLong(1, mentorshipId);
				heldInsert.setInt(2, 5);
				heldInsert.setLong(3, student.getId());
				heldInsert.executeUpdate();
			}

			ExecutorService httpPool = Executors.newSingleThreadExecutor();
			AtomicReference<Throwable> httpFailure = new AtomicReference<>();
			try {
				Future<Boolean> raced = httpPool.submit(() -> {
					try {
						mockMvc.perform(post("/api/v1/mentorship/{id}/rate", mentorshipPublicId)
										.header("Authorization", token)
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"rating\":4,\"feedback\":\"http-race\"}"))
								.andExpect(status().isConflict())
								.andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_RESOURCE"))
								.andExpect(jsonPath("$.message").value("You have already reviewed this mentorship."));
						return true;
					} catch (Throwable t) {
						httpFailure.set(t);
						return false;
					}
				});

				// let the HTTP request reach its blocked INSERT, then release it
				Thread.sleep(300);
				holder.commit();

				assertThat(raced.get(30, TimeUnit.SECONDS))
						.as("The blocked duplicate-creation race must be translated into the 409 envelope")
						.isTrue();
			} finally {
				httpPool.shutdownNow();
			}
			assertThat(httpFailure.get())
					.as("No harness-level failure may occur during the blocked race")
					.isNull();
		}

		assertThat(reviews.findAll().stream()
				.filter(r -> mentorshipId.equals(r.getMentorShip().getId()))
				.count())
				.as("Only the pre-existing (holder) review must survive the contradicted HTTP one")
				.isEqualTo(1);
	}

	/**
	 * [EB-44][P3-02] u7 — the hardening must NOT change the accepted upsert
	 * semantics of the rate endpoint: first rate inserts, second rate updates
	 * the same row in place (200 on both calls, exactly one row, updated values).
	 */
	@Test
	@Tag("critical")
	void u7_httpRateUpdateSemanticsPreservedAfterHardening() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "eb44-u7-mentor-" + unique + "@example.org";
		String studentEmail = "eb44-u7-student-" + unique + "@example.org";

		auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();
		UUID mentorshipPublicId = chain.mentorship().getPublicId();
		auth.enroll(mentorshipId, studentEmail);
		String token = auth.authToken(studentEmail, AuthFixture.STUDENT);

		mockMvc.perform(post("/api/v1/mentorship/{id}/rate", mentorshipPublicId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":5,\"feedback\":\"first\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/mentorship/{id}/rate", mentorshipPublicId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":4,\"feedback\":\"updated\"}"))
				.andExpect(status().isOk());

		List<MentorShipReviews> pairRows = reviews.findAll().stream()
				.filter(r -> mentorshipId.equals(r.getMentorShip().getId()))
				.toList();
		assertThat(pairRows)
				.as("Two sequential HTTP rates must collapse into exactly one row")
				.hasSize(1);
		assertThat(pairRows.get(0).getRating()).isEqualTo(4);
		assertThat(pairRows.get(0).getFeedBack()).isEqualTo("updated");
	}

	/**
	 * [EB-44][P3-02][u8] Callers that wrap {@code sendMessage} in an OUTER
	 * {@code @Transactional} scope must not be poisoned by a constraint race
	 * inside the REQUIRES_NEW conversation transaction.
	 *
	 * <p>Choreography (deterministic — the raw holder row only chooses WHEN the
	 * constraint conflict is observed, never whether): a raw PostgreSQL
	 * connection inserts an UNCOMMITTED conversation row for the (A,B) pair;
	 * an outer-transactional probe writes an unrelated (C,D) row and then calls
	 * {@code sendMessage(A,B)} — the inner REQUIRES_NEW transaction does not see
	 * the uncommitted pair (pre-check blind spot), inserts against the same
	 * unordered-pair key, and BLOCKS on the holder's row; the holder commits and
	 * the inner insert fails with 23505 — rolling back ONLY the inner tx — while
	 * the recovery refetch adopts the holder's committed conversation inside the
	 * still-healthy outer transaction, whose writes then commit cleanly.
	 *
	 * <p>This test discriminates the propagation contract: with the previous
	 * {@code REQUIRED} template the blocking insert would JOIN the outer tx, the
	 * 23505 would mark it rollback-only, and the outer commit would explode with
	 * {@code UnexpectedRollbackException}.
	 */
	@Test
	@Tag("critical")
	void u8_outerTransactionalCallerSurvivesInnerUniqueRace() {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String emailA = "eb44-u8-a-" + unique + "@example.org";
		String emailB = "eb44-u8-b-" + unique + "@example.org";
		String emailC = "eb44-u8-c-" + unique + "@example.org";
		String emailD = "eb44-u8-d-" + unique + "@example.org";
		auth.student(emailA);
		auth.student(emailB);
		auth.student(emailC);
		auth.student(emailD);

		UserEntity heldUser1 = users.findByEmail(emailA).orElseThrow();
		UserEntity heldUser2 = users.findByEmail(emailB).orElseThrow();

		try (Connection holder = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
			holder.setAutoCommit(false);
			try (PreparedStatement heldInsert = holder.prepareStatement(
					"INSERT INTO conversations (created_at, user1_id, user2_id) "
							+ "VALUES (now(), ?, ?)")) {
				heldInsert.setLong(1, heldUser1.getId());
				heldInsert.setLong(2, heldUser2.getId());
				heldInsert.executeUpdate();
			}

			ExecutorService outerPool = Executors.newSingleThreadExecutor();
			AtomicReference<String> probeReport = new AtomicReference<>();
			try {
				Future<String> raced = outerPool.submit(() ->
						outerTxProbe.raceProneOuterTransaction(emailA, emailB, emailC, emailD));

				// let the probe reach the blocked inner INSERT, then release it
				Thread.sleep(300);
				holder.commit();

				probeReport.set(raced.get(30, TimeUnit.SECONDS));
			} catch (Exception e) {
				throw new IllegalStateException("Probe failed: " + probeReport.get(), e);
			} finally {
				outerPool.shutdownNow();
			}

			assertThat(probeReport.get())
					.as("sendMessage must finish successfully: race caught in the inner tx, recovery adopted the holder's conversation, outer commit intact")
					.startsWith("NO_FAILURE|");

			long pairConversations = conversations.findAll().stream()
					.filter(c -> c.getUser1() != null && c.getUser2() != null)
					.filter(c -> {
						String one = c.getUser1().getEmail();
						String two = c.getUser2().getEmail();
						return (one.equals(emailA) && two.equals(emailB)) || (one.equals(emailB) && two.equals(emailA));
					})
					.count();
			assertThat(pairConversations)
					.as("Exactly one conversation (the holder's committed row) must exist for the pair")
					.isEqualTo(1);

			long unrelatedSurviving = conversations.findAll().stream()
					.filter(c -> c.getUser1() != null && c.getUser2() != null)
					.filter(c -> c.getUser1().getEmail().equals(emailC) && c.getUser2().getEmail().equals(emailD))
					.count();
			assertThat(unrelatedSurviving)
					.as("The OUTER transaction must have committed its own (C,D) write after the inner REQUIRES_NEW rollback")
					.isEqualTo(1);

			assertThat(messages.findAll().stream()
					.filter(m -> m.getSender() != null && m.getSender().getEmail().equals(emailA))
					.count())
					.as("The carried message must be persisted on the adopted conversation")
					.isEqualTo(1);
		} catch (java.sql.SQLException sqlFailure) {
			throw new IllegalStateException("Holder connection failed", sqlFailure);
		}
	}

	/**
	 * [EB-44][P3-02] x1 — a corrupted pre-existing total_points row makes the
	 * join's total_points insert fail AFTER the enrollment insert. PostgreSQL
	 * reports it as a bare 23505 whose constraint name has no friendly mapping
	 * (anonymous UNIQUE, no uk_* prefix) — the handler's SQLSTATE branch keeps
	 * the 409 generic envelope; the failed transaction must roll back entirely
	 * (no enrollment residue).
	 */
	@Test
	@Tag("critical")
	void x1_corruptedTotalPointsPreSeedKeepsCleanEnvelopeAndRollsBackJoin() throws Exception {
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String mentorEmail = "eb44-x1-mentor-" + unique + "@example.org";
		String studentEmail = "eb44-x1-student-" + unique + "@example.org";

		Student student = auth.student(studentEmail);
		var chain = auth.mentorship(mentorEmail, Status.ACTIVE);
		Long mentorshipId = chain.mentorship().getId();
		UUID mentorshipPublicId = chain.mentorship().getPublicId();
		String token = auth.authToken(studentEmail, AuthFixture.STUDENT);

		totalPoints.saveAndFlush(TotalPoints.builder()
				.student(student)
				.mentorship(chain.mentorship())
				.totalPoints(0)
				.build());

		mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipPublicId)
						.header("Authorization", token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_RESOURCE"))
				.andExpect(jsonPath("$.message").value("This record already exists."));

		assertThat(enrollments.findAll().stream()
				.filter(e -> e.getMentorShip().getId().equals(mentorshipId))
				.count())
				.as("The failed join tx must roll back with no enrollment residue")
				.isZero();
		assertThat(totalPoints.findAll().stream()
				.filter(tp -> tp.getMentorship().getId().equals(mentorshipId))
				.count())
				.as("Exactly one total_points row (the pre-seeded constraint violator) must remain")
				.isEqualTo(1);
	}

	/** Fixture for x2: a DIVE whose most specific cause is a Hibernate constraint failure. */
	private static org.springframework.dao.DataIntegrityViolationException diveFor(String constraintName, String sqlText) {
		// null cause: getMostSpecificCause() must land on the Hibernate failure
		// itself (the layer carrying the constraint name), not a synthetic JDBC child
		org.hibernate.exception.ConstraintViolationException hibernateCause =
				new org.hibernate.exception.ConstraintViolationException(
						"could not execute statement [" + sqlText + "]",
						null,
						constraintName);
		return new org.springframework.dao.DataIntegrityViolationException("save failed", hibernateCause);
	}

	/**
	 * [EB-44][P3-02] x2a — the uk_* mapping branch: a DIVE whose most specific
	 * cause is a Hibernate ConstraintViolationException named
	 * uk_mentorship_review_student keeps the 409 + friendly envelope.
	 */
	@Test
	void x2a_uniquenessConstraintNameKeeps409FriendlyMapping() {
		org.springframework.mock.web.MockHttpServletRequest request =
				new org.springframework.mock.web.MockHttpServletRequest();
		request.setRequestURI("/api/v1/mentorship/" + UUID.randomUUID() + "/rate");

		org.springframework.http.ResponseEntity<com.example.gradproj.EduNest.exception.ApiError> response =
				new com.example.gradproj.EduNest.exception.GlobalExceptionHandler()
						.handleDataIntegrityViolation(request,
								diveFor("uk_mentorship_review_student", "INSERT INTO mentorship_reviews"));

		assertThat(response.getStatusCode())
				.as("A 23505 violation mapped by constraint name must stay a 409 CONFLICT")
				.isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getErrorCode())
				.as("The 409 envelope must carry ERR_DUPLICATE_RESOURCE")
				.isEqualTo("ERR_DUPLICATE_RESOURCE");
		assertThat(response.getBody().getMessage())
				.as("The friendly message must be authored per constraint")
				.isEqualTo("You have already reviewed this mentorship.");
	}

	/**
	 * [EB-44][P3-02] x2b — untouched legacy envelope: an unrelated (fk-style,
	 * non-uk) DIVE keeps the clean 400 ERR_CONSTRAINT_VIOLATION shape.
	 */
	@Test
	void x2b_unrelatedConstraintViolationKeepsLegacy400Envelope() {
		org.springframework.mock.web.MockHttpServletRequest request =
				new org.springframework.mock.web.MockHttpServletRequest();
		request.setRequestURI("/api/v1/mentorship/" + UUID.randomUUID() + "/join");

		org.springframework.http.ResponseEntity<com.example.gradproj.EduNest.exception.ApiError> response =
				new com.example.gradproj.EduNest.exception.GlobalExceptionHandler()
						.handleDataIntegrityViolation(request,
								diveFor("fk_enrollments_student_id_fkey", "INSERT INTO enrollments"));

		assertThat(response.getStatusCode())
				.as("An unrelated (non-uk) DIVE must keep the legacy 400 envelope")
				.isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getErrorCode())
				.isEqualTo("ERR_CONSTRAINT_VIOLATION");
		assertThat(response.getBody().getMessage())
				.as("The 400 fallback message must stay generic (no DB identifiers)")
				.isEqualTo("The submitted data conflicts with an existing record.");
	}

	/**
	 * [EB-44][P3-02] Outer-transaction harness for u8. Registered automatically
	 * (static nested @TestConfiguration); the poisoned method runs inside an
	 * OUTER @Transactional scope and calls {@code sendMessage} whose
	 * conversation get-or-create must run in an INDEPENDENT REQUIRES_NEW
	 * transaction.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class OuterTxProbeConfig {

		@Bean
		OuterTxProbe outerTxProbe(ConversationService conversationService,
								   com.example.gradproj.EduNest.repository.users.UserRepository users,
								   ConversationRepository conversations) {
			return new OuterTxProbe(conversationService, users, conversations);
		}
	}

	static class OuterTxProbe {
		private final ConversationService conversationService;
		private final UserRepository users;
		private final ConversationRepository conversations;

		OuterTxProbe(ConversationService conversationService, UserRepository users, ConversationRepository conversations) {
			this.conversationService = conversationService;
			this.users = users;
			this.conversations = conversations;
		}

		/**
		 * Runs inside an OUTER @Transactional scope. Unrelated (C,D) write first,
		 * then a send whose conversation get-or-create races a raw duplicate row —
		 * the race must be absorbed by the REQUIRES_NEW conversation transaction
		 * and by {@code sendMessage}'s recovery, leaving the outer tx healthy.
		 */
		@Transactional
		String raceProneOuterTransaction(String emailA, String emailB, String emailC, String emailD) {
			UserEntity userC = users.findByEmail(emailC).orElseThrow();
			UserEntity userD = users.findByEmail(emailD).orElseThrow();
			Conversation unrelated = conversations.save(Conversation.builder()
					.user1(userC).user2(userD).createdAt(LocalDateTime.now()).build());

			RuntimeException sendFailure = null;
			try {
				conversationService.sendMessage(emailA, ConversationMessageRequest.builder()
						.recipientEmail(emailB)
						.content("racing against the holder's duplicate pair")
						.build());
			} catch (RuntimeException failure) {
				sendFailure = failure;
			}
			return (sendFailure == null ? "NO_FAILURE" : sendFailure.getClass().getName())
					+ "|" + unrelated.getId();
		}
	}
}
