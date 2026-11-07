package com.example.gradproj.EduNest.service.certificate;

import edunestprototype.PrototypeCertificate;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only JDBC experiment; it does not exercise Hibernate or production issuance. */
@Testcontainers(disabledWithoutDocker = true)
class CertificateSnapshotPostgresPrototypeTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void statementSnapshotSurvivesConcurrentWritesAndStreamsDeterministicBatches() throws Exception {
        int size = 12_001;
        int batchSize = 127;
        try (Connection setup = connect(); Statement sql = setup.createStatement()) {
            sql.execute("CREATE TABLE prototype_points (mentorship_id bigint NOT NULL, student_id bigint NOT NULL, "
                    + "points integer NOT NULL, PRIMARY KEY (mentorship_id, student_id))");
            // Every adjacent pair ties, including pairs straddling batch boundaries.
            sql.execute("INSERT INTO prototype_points SELECT 1, n, 10000 - ((n - 1) / 2) FROM generate_series(1, " + size + ") n");
            sql.execute("INSERT INTO prototype_points VALUES (2, 90001, 50000)");
        }
        try (Connection reader = connect(); Connection writer = connect()) {
            reader.setAutoCommit(false);
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            writer.setAutoCommit(false);
            try (Statement sql = writer.createStatement()) {
                // This uncommitted update must not be included in the materialization.
                sql.executeUpdate("UPDATE prototype_points SET points = 90000 WHERE mentorship_id = 1 AND student_id = 1");
            }
            try (Statement sql = reader.createStatement()) {
                sql.execute("CREATE TEMP TABLE prototype_candidates ON COMMIT DROP AS "
                        + "SELECT student_id, points, ROW_NUMBER() OVER (ORDER BY points DESC, student_id ASC) AS rank, "
                        + "COUNT(*) OVER () AS cohort, statement_timestamp() AS issued_at "
                        + "FROM prototype_points WHERE mentorship_id = 1");
                sql.execute("CREATE UNIQUE INDEX prototype_candidates_rank ON prototype_candidates(rank)");
                sql.execute("CREATE UNIQUE INDEX prototype_candidates_student ON prototype_candidates(student_id)");
            }
            writer.commit();
            try (Statement sql = writer.createStatement()) {
                sql.executeUpdate("UPDATE prototype_points SET points = 100000 WHERE mentorship_id = 1 AND student_id = 12001");
                sql.executeUpdate("INSERT INTO prototype_points VALUES (1, 13000, 200000)");
            }
            writer.commit();

            long lastRank = 0;
            int largestBatch = 0;
            int batchCount = 0;
            OffsetDateTime issuedAt = null;
            try (PreparedStatement page = reader.prepareStatement(
                    "SELECT student_id, points, rank, cohort, issued_at FROM prototype_candidates WHERE rank > ? ORDER BY rank LIMIT ?")) {
                while (true) {
                    page.setLong(1, lastRank);
                    page.setInt(2, batchSize);
                    int rowsInBatch = 0;
                    try (ResultSet rows = page.executeQuery()) {
                        while (rows.next()) {
                            long expectedRank = lastRank + 1;
                            assertThat(rows.getLong("rank")).isEqualTo(expectedRank);
                            assertThat(rows.getLong("student_id")).isEqualTo(expectedRank);
                            assertThat(rows.getInt("points")).isEqualTo(10000 - ((int) expectedRank - 1) / 2);
                            assertThat(rows.getLong("cohort")).isEqualTo(size);
                            var rowTimestamp = rows.getObject("issued_at", OffsetDateTime.class);
                            if (issuedAt == null) issuedAt = rowTimestamp;
                            assertThat(rowTimestamp).isEqualTo(issuedAt);
                            lastRank = expectedRank;
                            rowsInBatch++;
                        }
                    }
                    if (rowsInBatch == 0) break;
                    batchCount++;
                    largestBatch = Math.max(largestBatch, rowsInBatch);
                }
            }
            assertThat(lastRank).isEqualTo(size);
            assertThat(issuedAt).isNotNull();
            assertThat(largestBatch).isEqualTo(batchSize);
            assertThat(batchCount).isGreaterThan(1);
            reader.commit();
            try (Statement sql = reader.createStatement(); ResultSet result = sql.executeQuery(
                    "SELECT to_regclass('pg_temp.prototype_candidates')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isNull();
            }
            reader.rollback();
        }
    }

    @Test
    void emptyCandidateSetHasNoRowsAndIsDiscardedOnRollback() throws Exception {
        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.execute("CREATE TEMP TABLE prototype_empty_source (student_id bigint, points integer) ON COMMIT DROP");
            sql.execute("CREATE TEMP TABLE prototype_empty ON COMMIT DROP AS "
                    + "SELECT student_id, points, ROW_NUMBER() OVER (ORDER BY points DESC, student_id ASC) AS rank, "
                    + "COUNT(*) OVER () AS cohort FROM prototype_empty_source");
            try (ResultSet rows = sql.executeQuery("SELECT count(*) FROM prototype_empty")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
            }
            connection.rollback();
            try (ResultSet rows = sql.executeQuery("SELECT to_regclass('pg_temp.prototype_empty')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
            }
            connection.rollback();
        }
    }

    @Test
    void syntheticHibernateBatchCanBoundManagedEntitiesButClearsOuterState() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.url", postgres.getJdbcUrl())
                .applySetting("hibernate.connection.username", postgres.getUsername())
                .applySetting("hibernate.connection.password", postgres.getPassword())
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try (SessionFactory factory = new MetadataSources(registry)
                .addAnnotatedClass(PrototypeCertificate.class).buildMetadata().buildSessionFactory();
             var session = factory.openSession()) {
            var tx = session.beginTransaction();
            session.doWork(connection -> {
                try (Statement sql = connection.createStatement()) {
                    sql.execute("CREATE TEMP TABLE prototype_certificate (id bigint PRIMARY KEY) ON COMMIT DROP");
                }
            });
            var outerManaged = new PrototypeCertificate(0);
            session.persist(outerManaged);
            int maxManaged = 0;
            for (int i = 1; i <= 1_000; i++) {
                session.persist(new PrototypeCertificate(i));
                maxManaged = Math.max(maxManaged, ((SessionImplementor) session)
                        .getPersistenceContext().getNumberOfManagedEntities());
                if (i % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }
            assertThat(maxManaged).isLessThanOrEqualTo(51);
            // clear() also detaches entities that belong to the caller's outer transaction.
            assertThat(session.contains(outerManaged)).isFalse();
            assertThat(session.createQuery("select count(c) from PrototypeCertificate c", Long.class)
                    .getSingleResult()).isEqualTo(1_001L);
            tx.rollback();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
