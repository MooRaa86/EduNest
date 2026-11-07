package com.example.gradproj.EduNest.migration;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.util.PSQLException;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * EB-44 / [P3-02] — Uniqueness constraints with dedupe + conflict UX: PostgreSQL
 * migration lifecycle suite for {@code V3__uniqueness_constraints_and_dedupe.sql}
 * (successor to the EB-43 suite, same manual Flyway+EMF wiring, zero skipped-on-green).
 *
 * Covers (mirrors the approved plan, rule identifiers T1..T5):
 * T1  fresh database migrates V1 -> V2 -> V3 cleanly;
 * T2  dirty production-shaped dataset is deduped deterministically (earliest id
 *     wins per duplicate group), every removed row is fully archived into the
 *     eb44_archive_* tables, loser conversation messages are reassigned to the
 *     winner, NULL-participant conversations (and their messages) are archived
 *     and removed, and both conversation participant columns become NOT NULL;
 * T3  uniqueness layer rejects duplicate data at the database level (SQLSTATE
 *     23505, constraint name surfaced);
 * T4  Hibernate EntityManagerFactory boots with ddl-auto=validate against the
 *     post-V3 schema (entity baseline stays in lockstep with Flyway);
 * T5  re-running migrate is idempotent (0 migrations executed).
 */
@Tag("critical")
@Tag("migration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UniquenessPostgresMigrationIT {

	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

	private static Flyway flywayV1V2;
	private static Flyway flywayAll;
	private static String jdbcUrl;
	private static String username;
	private static String password;

	@BeforeAll
	static void initContainer() {
		// [EB-44] Docker is REQUIRED for this verification: a missing daemon must
		// FAIL hard (never assumption-skip) so dedicated CI runs cannot silently pass.
		String dockerState;
		try {
			dockerState = DockerClientFactory.instance().isDockerAvailable() ? "available" : "unavailable";
		} catch (Exception e) {
			dockerState = "unavailable (" + e.getMessage() + ")";
		}
		if (!"available".equals(dockerState)) {
			throw new IllegalStateException("Docker daemon unavailable — EB-44 PostgreSQL dedupe suite cannot "
					+ "execute (failed at: " + dockerState + "). This suite must never silently skip.");
		}
		POSTGRES.start();

		jdbcUrl = POSTGRES.getJdbcUrl();
		username = POSTGRES.getUsername();
		password = POSTGRES.getPassword();

		flywayV1V2 = Flyway.configure()
				.dataSource(jdbcUrl, username, password)
				.locations("classpath:db/migration")
				.target("2")
				.baselineOnMigrate(false)
				.cleanDisabled(true)
				.load();

		flywayAll = Flyway.configure()
				.dataSource(jdbcUrl, username, password)
				.locations("classpath:db/migration")
				.baselineOnMigrate(false)
				.cleanDisabled(true)
				.placeholders(java.util.Map.of("defaultCommissionRate", "10.0"))
				.load();
	}

	/* ============ shared seeding helpers (V1/V2 state) ============ */

	private static void execute(String sql) throws SQLException {
		try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
			 Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
		}
	}

	private static List<List<Object>> query(String sql) throws SQLException {
		List<List<Object>> rows = new ArrayList<>();
		try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
			 Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(sql)) {
			int cols = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				List<Object> row = new ArrayList<>();
				for (int i = 1; i <= cols; i++) {
					row.add(rs.getObject(i));
				}
				rows.add(row);
			}
		}
		return rows;
	}

	/** Verbatim EB-82-reconciled shapes for dirty rows (NOT NULL audit columns filled). */
	private static void seedDirtyDataset() throws SQLException {
		// role ids come from V1's deterministic role seeding: ADMIN=1, MENTOR=2, STUDENT=3
		for (long id : new long[]{90L}) {
			execute("INSERT INTO users (deleted, enabled, created_at, id, role_id, updated_at, created_by, email, "
					+ "first_name, last_name, password, updated_by) VALUES (false, true, now(), " + id
					+ ", 2, now(), 'eb44', 'eb44-mentor" + id + "@example.org', 'Men', 'Tor', 'x', 'eb44')");
			execute("INSERT INTO mentors (mentor_id, years_of_experience, job_title) VALUES (" + id + ", 9.0, 'Mentor')");
		}
		for (long id : new long[]{100L, 101L, 102L, 103L}) {
			execute("INSERT INTO users (deleted, enabled, created_at, id, role_id, updated_at, created_by, email, "
					+ "first_name, last_name, password, updated_by) VALUES (false, true, now(), " + id
					+ ", 3, now(), 'eb44', 'eb44-student" + id + "@example.org', 'Stu', 'Dent', 'x', 'eb44')");
			execute("INSERT INTO students (student_id, educational_level, job_title) VALUES ("
					+ id + ", 'JUNIOR', 'Student')");
		}
		execute("INSERT INTO mentorship (discount_percentage, duration, price, rating, created_at, id, mentor_id, "
				+ "updated_at, category, description, created_by, status, subtitle, title, updated_by) VALUES "
				+ "(10, 10.0, 180.0, 4.5, now(), 500, 90, now(), 'Programming', 'EB-44 seed', 'eb44', "
				+ "'ACTIVE', 'Seed', 'EB-44 Seed Mentorship', 'eb44')");

		// MSH-04 dirty enrollments: U100 is enrolled twice in M500 (700 winner, 701 loser) + clean U101
		for (long id : new long[]{700L, 701L, 702L}) {
			long studentId = (id == 702L) ? 101L : 100L;
			execute("INSERT INTO enrollments (platform_profit, price, created_at, id, joined_at, mentorship_id, "
					+ "student_id, updated_at, created_by, updated_by) VALUES "
					+ "(18.0, 162.0, now(), " + id + ", now(), 500, " + studentId + ", now(), 'eb44', 'eb44')");
		}

		// dirty reviews: U100/M500 duplicated (750 winner, 751 loser) + two identical NULL/NULL degenerate rows (753, 754)
		execute("INSERT INTO mentorship_reviews (created_at, id, mentorship_id, rating, student_id, updated_at, "
				+ "feed_back, created_by, updated_by) VALUES (now(), 750, 500, 4, 100, now(), 'first', 'eb44', 'eb44')");
		execute("INSERT INTO mentorship_reviews (created_at, id, mentorship_id, rating, student_id, updated_at, "
				+ "feed_back, created_by, updated_by) VALUES (now(), 751, 500, 2, 100, now(), 'second', 'eb44', 'eb44')");
		execute("INSERT INTO mentorship_reviews (created_at, id, mentorship_id, rating, student_id, updated_at, "
				+ "feed_back, created_by, updated_by) VALUES (now(), 753, NULL, 1, NULL, now(), 'degenerate-a', 'eb44', 'eb44')");
		execute("INSERT INTO mentorship_reviews (created_at, id, mentorship_id, rating, student_id, updated_at, "
				+ "feed_back, created_by, updated_by) VALUES (now(), 754, NULL, 1, NULL, now(), 'degenerate-b', 'eb44', 'eb44')");

		// CHAT-05 dirty conversations: pair (U100,U101) exists as 800; duplicated as 801 (reversed) and 802;
		// 805 is a NULL-participant degenerate row with its own message; 806 is a clean distinct pair
		execute("INSERT INTO conversations (created_at, id, user1_id, user2_id) VALUES (now(), 800, 100, 101)");
		execute("INSERT INTO conversations (created_at, id, user1_id, user2_id) VALUES (now(), 801, 101, 100)");
		execute("INSERT INTO conversations (created_at, id, user1_id, user2_id) VALUES (now(), 802, 100, 101)");
		execute("INSERT INTO conversations (created_at, id, user1_id, user2_id) VALUES (now(), 805, 102, NULL)");
		execute("INSERT INTO conversations (created_at, id, user1_id, user2_id) VALUES (now(), 806, 103, 102)");

		// messages in loser conversations (must be reassigned to winner 800) and in remove-prone 805 (must be archived)
		execute("INSERT INTO messages (seen, updated, conversation_id, id, sender_id, sent_at, content) "
				+ "VALUES (false, false, 801, 9001, 100, now(), 'message in reversed loser')");
		execute("INSERT INTO messages (seen, updated, conversation_id, id, sender_id, sent_at, content) "
				+ "VALUES (true, false, 802, 9002, 101, now(), 'message in same-pair loser')");
		execute("INSERT INTO messages (seen, updated, conversation_id, id, sender_id, sent_at, content) "
				+ "VALUES (false, false, 805, 9009, 102, now(), 'message with null-participant conversation')");
	}

	/* ============ T1 ============ */
	@Test
	@Order(1)
	@DisplayName("T1. Baseline V1+V2 migrate succeeds, then dirty dataset seeds without constraints")
	void t1_baselineMigrateAndSeedDirtyData() throws SQLException {
		MigrateResult baseline = flywayV1V2.migrate();
		assertThat(baseline.success)
				.as("V1+V2 baseline migration must succeed on a fresh database")
				.isTrue();
		assertThat(baseline.migrationsExecuted)
				.as("Baseline must execute exactly 2 scripts (V1, V2)")
				.isEqualTo(2);

		int enrollmentsBefore = query("SELECT count(*) FROM enrollments").get(0).get(0) instanceof Integer i ? i : 0;
		assertThat(enrollmentsBefore).isZero();

		seedDirtyDataset();

		assertThat(query("SELECT count(*) FROM enrollments").get(0).get(0))
				.as("3 dirty enrollment rows seeded (one duplicate pair + one clean row)")
				.hasToString("3");
		assertThat(query("SELECT count(*) FROM conversations").get(0).get(0))
				.as("5 dirty conversations seeded")
				.hasToString("5");
	}

	/* ============ T2 ============ */
	@Test
	@Order(2)
	@DisplayName("T2. V3 migrates dirty data: earliest-winner dedupe, full archival, message reassignment, NOT NULL participants")
	void t2_dedupeMigrationWithArchival() throws SQLException {
		MigrateResult result = flywayAll.migrate();
		assertThat(result.success)
				.as("V3 dedupe migration (including the in-file verification block) must succeed")
				.isTrue();
		// EB-44 scoped V3..V6 dedupe/baseline work; subsequent additive migrations (V7, V8, ...) ride along.
		assertThat(result.migrationsExecuted)
				.as("Dedupe migration and subsequent additive migrations must succeed")
				.isGreaterThanOrEqualTo(4);

		// --- enrollments: winner kept, loser archived verbatim with archive metadata ---
		assertThat(query("SELECT id FROM enrollments ORDER BY id"))
				.as("Enrollments dedupe: earliest id wins per (student_id, mentorship_id) pair")
				.extracting(r -> r.get(0).toString())
				.containsExactly("700", "702");
		List<List<Object>> archivedEnrollments = query(
				"SELECT id, student_id, mentorship_id, archived_by, archived_at FROM eb44_archive_enrollments");
		assertThat(archivedEnrollments)
				.as("The removed enrollment must be preserved in the archive with EB-44 metadata")
				.extracting(r -> r.get(0).toString())
				.containsExactly("701");
		assertThat(archivedEnrollments.get(0).get(1)).hasToString("100");
		assertThat(archivedEnrollments.get(0).get(2)).hasToString("500");
		assertThat(archivedEnrollments.get(0).get(3)).hasToString("EB-44-V3");
		assertThat(archivedEnrollments.get(0).get(4)).isNotNull();

		// --- reviews: pair dedupe + degenerate NULL/NULL collapse, both archived ---
		assertThat(query("SELECT id FROM mentorship_reviews ORDER BY id"))
				.extracting(r -> r.get(0).toString())
				.as("Reviews dedupe: (100,500) collapsed to 750, identical NULL/NULL rows collapsed to 753")
				.containsExactly("750", "753");
		assertThat(query("SELECT id FROM eb44_archive_mentorship_reviews ORDER BY id"))
				.extracting(r -> r.get(0).toString())
				.as("Both removed review rows must be archived")
				.containsExactly("751", "754");

		// --- conversations: unordered-pair dedupe, NULL-participant removal, NOT NULL invariant ---
		assertThat(query("SELECT id, user1_id, user2_id FROM conversations ORDER BY id"))
				.as("Conversations dedupe: (100,101) pair survives only as earliest id 800; 806 untouched; 801/802/805 removed")
				.extracting(r -> r.get(0).toString() + "(" + r.get(1) + "," + r.get(2) + ")")
				.containsExactly("800(100,101)", "806(103,102)");
		List<List<Object>> archivedConversations = query(
				"SELECT id FROM eb44_archive_conversations ORDER BY id");
		assertThat(archivedConversations)
				.as("All removed conversation rows must be archived")
				.extracting(r -> r.get(0).toString())
				.containsExactly("801", "802", "805");

		assertThat(query("""
				SELECT count(*) FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'conversations'
				  AND column_name IN ('user1_id', 'user2_id') AND is_nullable = 'NO'
				""").get(0).get(0))
				.as("V3 must enforce NOT NULL on conversations.user1_id and user2_id")
				.hasToString("2");

		// --- messages: loser-conversation history reassigned to the winner, degenerate archived ---
		assertThat(query("SELECT id, conversation_id FROM messages WHERE id IN (9001, 9002) ORDER BY id"))
				.as("Messages from both loser conversations must be reassigned to winner conversation 800")
				.extracting(r -> r.get(0) + "->" + r.get(1))
				.containsExactly("9001->800", "9002->800");
		List<List<Object>> archivedMessages = query(
				"SELECT id, conversation_id, content FROM eb44_archive_messages");
		assertThat(archivedMessages)
				.as("Messages of archived conversations must be preserved verbatim in the archive")
				.extracting(r -> r.get(0) + "->" + r.get(1))
				.containsExactly("9009->805");
		assertThat(archivedMessages.get(0).get(2))
				.as("Archived content preserved verbatim")
				.hasToString("message with null-participant conversation");

		// --- uniqueness DDL exists ---
		List<List<Object>> indexes = query("""
				SELECT indexname FROM pg_indexes
				WHERE schemaname = 'public'
				  AND indexname IN ('uk_enrollment_student_mentorship', 'uk_mentorship_review_student',
				                    'uk_conversation_unordered_pair', 'idx_enrollments_mentorship',
				                    'idx_enrollments_student_mentorship', 'idx_mentorship_student')
				  ORDER BY indexname
				""");
		List<String> indexNames = indexes.stream().map(r -> r.get(0).toString()).toList();
		assertThat(indexNames)
				.as("V3 must create the uniqueness/covering indexes")
				.contains("uk_enrollment_student_mentorship", "uk_mentorship_review_student",
						"uk_conversation_unordered_pair", "idx_enrollments_mentorship");
		assertThat(indexNames)
				.as("V3 must retire the redundant pair indexes")
				.doesNotContain("idx_enrollments_student_mentorship", "idx_mentorship_student");
	}

	/* ============ T3 ============ */
	@Test
	@Order(3)
	@DisplayName("T3. Database level rejects duplicates (SQLSTATE 23505 + constraint name) and NULL participants (23502)")
	void t3_uniquenessLayerRejectsBadData() {
		// duplicate unordered conversation pair
		PSQLException convDup = catchThrowableOfType(
				() -> execute("INSERT INTO conversations (created_at, user1_id, user2_id) VALUES (now(), 100, 101)"),
				PSQLException.class);
		assertThat((Object) convDup).as("Duplicate unordered conversation pair must be rejected").isNotNull();
		assertThat(convDup.getSQLState()).as("Unique violation SQLSTATE").isEqualTo("23505");
		assertThat(convDup.getServerErrorMessage().getConstraint())
				.as("Constraint name must identify the unordered-pair unique index")
				.isEqualTo("uk_conversation_unordered_pair");

		// reversed orientation still rejected (functional expression index)
		PSQLException convReversed = catchThrowableOfType(
				() -> execute("INSERT INTO conversations (created_at, user1_id, user2_id) VALUES (now(), 101, 100)"),
				PSQLException.class);
		assertThat(convReversed.getSQLState()).isEqualTo("23505");

		// duplicate enrollment pair
		PSQLException enrollDup = catchThrowableOfType(
				() -> execute("INSERT INTO enrollments (platform_profit, price, created_at, joined_at, mentorship_id, "
						+ "student_id, updated_at, created_by, updated_by) VALUES "
						+ "(0.0, 162.0, now(), now(), 500, 100, now(), 'eb44', 'eb44')"),
				PSQLException.class);
		assertThat((Object) enrollDup).as("Duplicate enrollment pair must be rejected").isNotNull();
		assertThat(enrollDup.getSQLState()).isEqualTo("23505");

		// duplicate review pair
		PSQLException reviewDup = catchThrowableOfType(
				() -> execute("INSERT INTO mentorship_reviews (created_at, mentorship_id, rating, student_id, updated_at, "
						+ "created_by, updated_by) VALUES (now(), 500, 5, 100, now(), 'eb44', 'eb44')"),
				PSQLException.class);
		assertThat(reviewDup.getSQLState()).isEqualTo("23505");

		// NULL participant now impossible
		PSQLException nullParticipant = catchThrowableOfType(
				() -> execute("INSERT INTO conversations (created_at, user1_id, user2_id) VALUES (now(), 100, NULL)"),
				PSQLException.class);
		assertThat((Object) nullParticipant).as("NULL participant must be rejected post-hygiene").isNotNull();
		assertThat(nullParticipant.getSQLState()).as("Not-null violation SQLSTATE").isEqualTo("23502");
	}

	/* ============ T4 ============ */
	@Test
	@Order(4)
	@DisplayName("T4. Hibernate validate: entities agree with the post-V3 production schema")
	void t4_hibernateValidateAgainstPostV3Schema() {
		LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
		emfBean.setPersistenceProviderClass(HibernatePersistenceProvider.class);
		emfBean.setPackagesToScan("com.example.gradproj.EduNest.entity");

		HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
		emfBean.setJpaVendorAdapter(vendorAdapter);

		java.util.Map<String, Object> jpaProperties = new java.util.HashMap<>();
		jpaProperties.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
		jpaProperties.put("jakarta.persistence.jdbc.url", jdbcUrl);
		jpaProperties.put("jakarta.persistence.jdbc.user", username);
		jpaProperties.put("jakarta.persistence.jdbc.password", password);
		jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
		jpaProperties.put("hibernate.hbm2ddl.auto", "validate");
		jpaProperties.put("hibernate.show_sql", "false");
		jpaProperties.put("hibernate.physical_naming_strategy",
				"org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
		jpaProperties.put("hibernate.implicit_naming_strategy",
				"org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");

		emfBean.setJpaPropertyMap(jpaProperties);

		org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
			emfBean.afterPropertiesSet();
			EntityManagerFactory emf = emfBean.getObject();
			assertThat(emf).isNotNull();
			assertThat(emf.isOpen()).isTrue();
			emf.close();
		}, "EntityManagerFactory with ddl-auto=validate must boot cleanly against the post-V3 schema");
	}

	/* ============ T5 ============ */
	@Test
	@Order(5)
	@DisplayName("T5. Repeat migrate() against the deduped database executes 0 migrations (including verification block)")
	void t5_repeatedMigrateIsIdempotent() {
		MigrateResult repeat = flywayAll.migrate();
		assertThat(repeat.success).isTrue();
		assertThat(repeat.migrationsExecuted)
				.as("Subsequent full migrate() must be a no-op")
				.isEqualTo(0);
	}
}
