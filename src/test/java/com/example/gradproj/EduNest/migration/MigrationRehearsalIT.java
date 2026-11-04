package com.example.gradproj.EduNest.migration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** EB-49: two independent, synthetic, container-owned migration and restore rehearsals. */
class MigrationRehearsalIT {
    static final String LABEL = "org.edunest.eb49.run";
    static final String SOURCE = "eb49_source";
    static final String MIGRATED = "eb49_migrated";
    static final String RECOVERED = "eb49_recovered";
    private static final String ARCHIVE = "/tmp/eb49-synthetic.dump";

    @Test
    void twoIndependentRehearsals() throws Exception {
        // Avoid emitting connection strings from infrastructure libraries.
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);
        ((Logger) LoggerFactory.getLogger("tc")).setLevel(Level.ERROR);
        for (int run = 1; run <= 2; run++) {
            executeRun(run);
        }
    }

    private void executeRun(int ordinal) throws Exception {
        String runId = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString(); // disposable only; never in process arguments
        String gitSha = gitSha();
        Instant started = Instant.now();
        long startedNs = System.nanoTime();
        List<String> evidence = new ArrayList<>();
        evidence.add("task=EB-49");
        evidence.add("run_id=" + runId);
        evidence.add("run_number=" + ordinal);
        evidence.add("started_utc=" + started);
        evidence.add("git_commit=" + gitSha);
        evidence.add("fixture=synthetic-pre-v3-v1; source=repository-generated; no production records");
        evidence.add("fixture_sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("ops/rehearsals/fixture-pre-v3.sql")))));
        evidence.add("worktree_note=commit_identifies_base_only; rehearsal_fixture_and_runner_may_be_uncommitted");
        PostgreSQLContainer<?> db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName(SOURCE).withUsername("eb49").withPassword(password)
                .withLabel(LABEL, runId)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))));
        boolean cleanupOk = false;
        Exception failure = null;
        String stage = "container_start";
        try {
            db.start();
            ownership(db, runId);
            if (!db.getHost().equals("localhost") && !db.getHost().equals("127.0.0.1") && !db.getHost().equals("::1")) {
                throw new IllegalStateException("Non-local container host rejected");
            }
            guardDatabase(db, SOURCE, runId);
            try (Connection source = connect(db, SOURCE, runId)) {
                check(scalar(source, "show server_version_num") / 10000 == 17, "PostgreSQL 17 required");
            evidence.add("postgres_version=" + db.execInContainer("postgres", "--version").getStdout().trim().replace(' ', '_'));
            }
            mark(evidence, "preflight_container_ownership_and_target", startedNs);

            // Establish an actual V1/V2 Flyway history in a new owned container, then seed only synthetic data.
            stage = "pre_migration_fixture";
            migrate(db, SOURCE, runId, "2", 2);
            try (Connection source = connect(db, SOURCE, runId)) {
                String fixture = Files.readString(Path.of("ops/rehearsals/fixture-pre-v3.sql"), StandardCharsets.UTF_8);
                try (var s = source.createStatement()) { s.execute(fixture); }
                check(scalar(source, "select count(*) from public.flyway_schema_history where success and version in ('1','2')") == 2, "Pre-migration history mismatch");
                check(scalar(source, "select count(*) from public.conversations") == 3, "Fixture mismatch");
            }
            mark(evidence, "pre_migration_synthetic_state", startedNs);

            stage = "backup";
            exec(db, runId, "backup", "sh", "-c", "PGPASSWORD=\"$POSTGRES_PASSWORD\" exec pg_dump -U eb49 -d eb49_source -Fc -n public --no-owner --no-privileges -f /tmp/eb49-synthetic.dump");
            exec(db, runId, "backup archive validation", "pg_restore", "--list", ARCHIVE);
            // Every fresh database already has public. Keep all application objects,
            // but omit that one schema-creation entry instead of dropping a schema.
            exec(db, runId, "restore list creation", "sh", "-c", "set -e; pg_restore --list /tmp/eb49-synthetic.dump > /tmp/eb49-all.list; sed '/ SCHEMA - public /d' /tmp/eb49-all.list > /tmp/eb49-restore.list");
            mark(evidence, "backup_custom_format", startedNs);

            stage = "restore_pre_migration_copy";
            createOwnedDatabase(db, MIGRATED, runId);
            restore(db, MIGRATED, runId);
            Map<String, String> sourceFingerprint;
            try (Connection source = connect(db, SOURCE, runId); Connection copy = connect(db, MIGRATED, runId)) {
                sourceFingerprint = fingerprint(source);
                compareFingerprints(sourceFingerprint, fingerprint(copy), "Restored copy");
            }
            mark(evidence, "restore_pre_migration_copy_and_compare", startedNs);
            stage = "migration_and_validation";
            migrate(db, MIGRATED, runId, "3", 1);
            try (Connection migrated = connect(db, MIGRATED, runId)) {
                validateMigrated(migrated);
                try (var s = migrated.createStatement(); var r = s.executeQuery("select version, type, checksum, success from public.flyway_schema_history order by installed_rank")) {
                    while (r.next()) evidence.add("migration=" + r.getString(1) + ",type=" + r.getString(2) + ",checksum=" + r.getString(3) + ",success=" + r.getBoolean(4));
                }
            }
            mark(evidence, "migrate_and_verify_dedupe_constraints_relationships", startedNs);

            // Recovery is a separate restore into a fresh owned database, not Flyway undo.
            stage = "recovery";
            createOwnedDatabase(db, RECOVERED, runId);
            restore(db, RECOVERED, runId);
            try (Connection recovered = connect(db, RECOVERED, runId)) {
                compareFingerprints(sourceFingerprint, fingerprint(recovered), "Recovery");
                check(scalar(recovered, "select count(*) from public.conversations") == 3, "Recovery relationship mismatch");
                check(scalar(recovered, "select count(*) from public.messages m join public.conversations c on c.id=m.conversation_id") == 2, "Recovery foreign-key relationship mismatch");
            }
            evidence.add("recovery_schema_and_all_public_table_row_hashes=MATCH");
            mark(evidence, "recover_fresh_database_and_compare", startedNs);
        } catch (Exception ex) {
            failure = ex;
            evidence.add("result=FAIL");
            evidence.add("failure_stage=" + stage);
            evidence.add("failure_type=" + ex.getClass().getSimpleName());
            if (ex instanceof IllegalStateException && ex.getMessage() != null && ex.getMessage().matches("[A-Za-z0-9 ()_-]+")) {
                evidence.add("failure_reason=" + ex.getMessage());
            }
        } finally {
            try {
                db.stop();
                cleanupOk = !db.isRunning();
            } catch (Exception ignored) {
                cleanupOk = false;
            }
            evidence.add("cleanup=" + (cleanupOk ? "PASS" : "FAIL"));
            evidence.add("elapsed_ms=" + (System.nanoTime() - startedNs) / 1_000_000);
            if (failure == null && cleanupOk) evidence.add("result=PASS");
            evidence.add("limitations=synthetic_fixture; selected_schema_metadata; check_reparse_not_semantic_proof; no_external_object_storage; no_post_backup_writes; no_flyway_undo");
            Path directory = Path.of("target", "rehearsal-evidence");
            Files.createDirectories(directory);
            Path report = directory.resolve("EB-49-" + runId + ".txt");
            Files.write(report, evidence, StandardCharsets.UTF_8);
            System.out.println("EB-49 report=" + report + " result=" + (failure == null && cleanupOk ? "PASS" : "FAIL"));
        }
        if (failure != null) throw new IllegalStateException("Rehearsal failed; see sanitized evidence (" + failure.getClass().getSimpleName() + ")");
        requireCleanup(cleanupOk);
    }

    private static String gitSha() throws Exception {
        Process p = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
        String sha = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0 || !sha.matches("[0-9a-f]{40}")) throw new IllegalStateException("Cannot identify repository commit");
        return sha;
    }

    private static void mark(List<String> evidence, String step, long start) {
        evidence.add("step=" + step + ",result=PASS,elapsed_ms=" + (System.nanoTime() - start) / 1_000_000);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    static void ownership(PostgreSQLContainer<?> db, String runId) {
        check(db.isRunning(), "Owned container not running");
        var info = DockerClientFactory.instance().client().inspectContainerCmd(db.getContainerId()).exec();
        check(runId.equals(info.getConfig().getLabels().get(LABEL)), "Container ownership mismatch");
    }

    static void exec(PostgreSQLContainer<?> db, String runId, String step, String... command) throws Exception {
        ownership(db, runId);
        var result = db.execInContainer(command);
        if (result.getExitCode() != 0) throw new IllegalStateException(step + " failed (exit " + result.getExitCode() + ")");
    }

    static void guardDatabase(PostgreSQLContainer<?> db, String database, String runId) throws Exception {
        check(List.of(SOURCE, MIGRATED, RECOVERED).contains(database), "Unexpected database name");
        String sql = "CREATE SCHEMA eb49_guard; CREATE TABLE eb49_guard.identity (run_id text NOT NULL); INSERT INTO eb49_guard.identity VALUES ('" + runId + "')";
        exec(db, runId, "target identity setup", "sh", "-c", "PGPASSWORD=\"$POSTGRES_PASSWORD\" exec psql -v ON_ERROR_STOP=1 -U eb49 -d " + database + " -c \"" + sql + "\"");
    }

    private static void createOwnedDatabase(PostgreSQLContainer<?> db, String name, String runId) throws Exception {
        check(List.of(MIGRATED, RECOVERED).contains(name), "Unexpected database name");
        exec(db, runId, "disposable database creation", "sh", "-c", "PGPASSWORD=\"$POSTGRES_PASSWORD\" exec createdb -U eb49 " + name);
        guardDatabase(db, name, runId);
        // A schema-scoped pg_dump does not include CREATE EXTENSION; V2 indexes
        // require pg_trgm to exist before pg_restore reconstructs those indexes.
        try (Connection connection = connect(db, name, runId); var statement = connection.createStatement()) {
            statement.execute("create extension if not exists pg_trgm");
        }
    }

    static Connection connect(PostgreSQLContainer<?> db, String name, String runId) throws Exception {
        ownership(db, runId);
        check(List.of(SOURCE, MIGRATED, RECOVERED).contains(name), "Unexpected database name");
        validateCredentials(db.getUsername(), db.getPassword());
        String url = "jdbc:postgresql://127.0.0.1:" + db.getMappedPort(5432) + "/" + name;
        Connection connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
        try (var s = connection.createStatement(); var r = s.executeQuery("select current_database(), run_id from eb49_guard.identity")) {
            check(r.next() && name.equals(r.getString(1)) && runId.equals(r.getString(2)) && !r.next(), "Database ownership marker mismatch");
        } catch (Exception e) {
            connection.close();
            throw new IllegalStateException("Disposable database identity check failed");
        }
        return connection;
    }

    static void migrate(PostgreSQLContainer<?> db, String name, String runId, String target, int expected) throws Exception {
        check(List.of("2", "3").contains(target), "Unexpected migration target");
        try (Connection ignored = connect(db, name, runId)) { /* validate the owned target immediately before Flyway */ }
        String url = "jdbc:postgresql://127.0.0.1:" + db.getMappedPort(5432) + "/" + name;
        var flyway = Flyway.configure().dataSource(url, db.getUsername(), db.getPassword()).schemas("public")
                .defaultSchema("public").baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .locations("filesystem:src/main/resources/db/migration").target(target).load();
        var result = flyway.migrate();
        check(result.success && result.migrationsExecuted == expected, "Unexpected Flyway migration result");
        flyway.validate();
    }

    private static void restore(PostgreSQLContainer<?> db, String name, String runId) throws Exception {
        try (Connection ignored = connect(db, name, runId)) { /* guard before restoring */ }
        exec(db, runId, "restore", "sh", "-c", "PGPASSWORD=\"$POSTGRES_PASSWORD\" exec pg_restore --exit-on-error --no-owner --no-privileges --use-list=/tmp/eb49-restore.list -U eb49 -d " + name + " /tmp/eb49-synthetic.dump");
    }

    private static long scalar(Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { check(r.next(), "Expected scalar result"); return r.getLong(1); }
    }

    static void validateMigrated(Connection c) throws Exception {
        check(scalar(c, "select count(*) from public.flyway_schema_history where success and version in ('1','2','3')") == 3, "Migration history mismatch");
        check(scalar(c, "select count(*) from public.flyway_schema_history") == 3, "Unexpected migration history entries");
        check(scalar(c, "select count(*) from public.flyway_schema_history where not success") == 0, "Failed migration entry");
        check(scalar(c, "select count(*) from public.flyway_schema_history where type='SQL' and version in ('1','2','3')") == 3, "Unexpected migration types");
        check(scalar(c, "select count(*) from pg_extension where extname='pg_trgm'") == 1, "pg_trgm missing");
        check(scalar(c, "select count(*) from pg_indexes where schemaname='public' and indexname in ('idx_mentorship_explore_trgm','idx_mentorship_active_price','idx_users_mentor_name_trgm','uk_enrollment_student_mentorship','uk_mentorship_review_student','uk_conversation_unordered_pair','idx_enrollments_mentorship')") == 7, "Expected index missing");
        check(scalar(c, "select count(*) from information_schema.columns where table_schema='public' and table_name='conversations' and column_name in ('user1_id','user2_id') and is_nullable='NO'") == 2, "Participant nullability mismatch");
        check(scalar(c, "select count(*) from pg_constraint where contype='f' and not convalidated and connamespace='public'::regnamespace") == 0, "Unvalidated foreign key");
        check(scalar(c, "select count(*) from public.eb44_archive_enrollments where id=906") == 1, "Enrollment archive mismatch");
        check(scalar(c, "select count(*) from public.eb44_archive_enrollments") == 1, "Enrollment archive count mismatch");
        check(scalar(c, "select count(*) from public.eb44_archive_mentorship_reviews where id=908") == 1, "Review archive mismatch");
        check(scalar(c, "select count(*) from public.eb44_archive_mentorship_reviews") == 1, "Review archive count mismatch");
        check(scalar(c, "select count(*) from public.eb44_archive_conversations where id in (910,911)") == 2, "Conversation archive mismatch");
        check(scalar(c, "select count(*) from public.eb44_archive_conversations") == 2, "Conversation archive count mismatch");
        check(scalar(c, "select count(*) from public.eb44_archive_messages where id=913 and conversation_id=911") == 1, "Message archive mismatch");
        check(scalar(c, "select count(*) from public.messages where id=912 and conversation_id=909") == 1, "Message relationship mismatch");
        check(scalar(c, "select count(*) from public.conversations where user1_id is null or user2_id is null") == 0, "Null conversation participant remains");
        check(scalar(c, "select count(*) from public.users") == 3, "Fixture user count mismatch");
        check(scalar(c, "select count(*) from public.students s join public.users u on u.id=s.student_id") == 2, "Student relationship mismatch");
        check(scalar(c, "select count(*) from (select student_id,mentorship_id from public.enrollments group by 1,2 having count(*)>1) x") == 0, "Duplicate enrollment remains");
        check(scalar(c, "select count(*) from (select student_id,mentorship_id from public.mentorship_reviews group by 1,2 having count(*)>1) x") == 0, "Duplicate review remains");
        check(scalar(c, "select count(*) from (select least(user1_id,user2_id), greatest(user1_id,user2_id) from public.conversations group by 1,2 having count(*)>1) x") == 0, "Duplicate conversation remains");
        expectSqlState(c, "insert into public.conversations (id,created_at,user1_id,user2_id) values (920,now(),903,902)", "23505");
        expectSqlState(c, "insert into public.conversations (id,created_at,user1_id,user2_id) values (920,now(),902,null)", "23502");
        expectSqlState(c, "insert into public.conversations (id,created_at,user1_id,user2_id) values (920,now(),902,99999)", "23503");
    }

    private static void expectSqlState(Connection c, String sql, String expectedState) throws Exception {
        check(c.getAutoCommit(), "Constraint verification requires autocommit");
        try (var statement = c.createStatement()) {
            statement.execute(sql);
            throw new IllegalStateException("Expected constraint rejection did not occur");
        } catch (java.sql.SQLException e) {
            check(expectedState.equals(e.getSQLState()), "Unexpected constraint rejection");
        }
    }

    static Map<String, String> fingerprint(Connection c) throws Exception {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        List<String> tables = new ArrayList<>();
        try (var s = c.createStatement(); var r = s.executeQuery("select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by table_name")) {
            while (r.next()) tables.add(r.getString(1));
        }
        check(tables.size() == 43, "Unexpected pre-migration table count");
        fingerprints.put("columns", queryHash(c, "select table_name,column_name,data_type,is_nullable,coalesce(column_default,''),is_identity,coalesce(identity_generation,'') from information_schema.columns where table_schema='public' order by table_name,ordinal_position"));
        try (var s = c.createStatement(); var r = s.executeQuery("select cls.relname, con.conname, con.contype, pg_get_constraintdef(con.oid), con.convalidated, con.condeferrable, con.condeferred from pg_constraint con join pg_class cls on cls.oid=con.conrelid where con.connamespace='public'::regnamespace order by 1,2")) {
            while (r.next()) {
                String key = "constraint_" + r.getString(1) + "_" + r.getString(2);
                MessageDigest item = MessageDigest.getInstance("SHA-256");
                // Reparse CHECKs on matching temporary columns, exactly as a restore
                // reparses DDL. Never remove casts, grouping or literal characters.
                String definition = r.getString(4);
                if ("c".equals(r.getString(3))) definition = reparseCheck(c, r.getString(1), definition);
                item.update((r.getString(3) + ":" + definition + ":" + r.getBoolean(5) + ":" + r.getBoolean(6) + ":" + r.getBoolean(7)).getBytes(StandardCharsets.UTF_8));
                fingerprints.put(key, HexFormat.of().formatHex(item.digest()));
            }
        }
        fingerprints.put("indexes", queryHash(c, "select tablename,indexname,indexdef from pg_indexes where schemaname='public' order by 1,2"));
        fingerprints.put("extensions", queryHash(c, "select extname,extversion from pg_extension order by extname"));
        fingerprints.put("sequences", queryHash(c, "select sequencename,coalesce(last_value::text,''),start_value,increment_by from pg_sequences where schemaname='public' order by sequencename"));
        for (String table : tables) {
            check(table.matches("[a-z0-9_]+"), "Unexpected table identifier");
            fingerprints.put("table_" + table, queryHash(c, "select count(*), md5(coalesce(string_agg(row_to_json(t)::text, E'\\n' order by row_to_json(t)::text),'')) from public.\"" + table + "\" t"));
        }
        return fingerprints;
    }

    static String reparseCheck(Connection c, String table, String definition) throws Exception {
        check(table.matches("[a-z0-9_]+"), "Unexpected table identifier");
        check(definition.startsWith("CHECK ("), "Unexpected check definition");
        try (var s = c.createStatement()) {
            s.execute("CREATE TEMP TABLE eb49_check_reparse (LIKE public.\"" + table + "\") ON COMMIT PRESERVE ROWS");
            try {
                s.execute("ALTER TABLE pg_temp.eb49_check_reparse ADD CONSTRAINT eb49_check " + definition);
                try (var r = s.executeQuery("select pg_get_constraintdef(oid) from pg_constraint where conrelid='pg_temp.eb49_check_reparse'::regclass and conname='eb49_check'")) {
                    check(r.next(), "Reparsed check missing");
                    String result = r.getString(1);
                    check(!r.next(), "Multiple reparsed checks");
                    return result;
                }
            } finally {
                s.execute("DROP TABLE pg_temp.eb49_check_reparse");
            }
        }
    }

    private static String queryHash(Connection c, String query) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var s = c.createStatement(); var r = s.executeQuery(query)) { hashRows(digest, r); }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void compareFingerprints(Map<String, String> expected, Map<String, String> actual, String label) {
        check(expected.keySet().equals(actual.keySet()), label + " fingerprint sections mismatch");
        for (String key : expected.keySet()) {
            check(expected.get(key).equals(actual.get(key)), label + " mismatch in " + key);
        }
    }

    static void validateCredentials(String username, String password) {
        check(username != null && !username.isBlank() && password != null && !password.isBlank(), "Disposable credentials missing");
    }

    static void requireCleanup(boolean cleanupOk) {
        check(cleanupOk, "Rehearsal container cleanup failed");
    }

    private static void hashRows(MessageDigest digest, ResultSet r) throws Exception {
        int columns = r.getMetaData().getColumnCount();
        while (r.next()) for (int i=1; i<=columns; i++) {
            String value = String.valueOf(r.getObject(i));
            digest.update((value.length() + ":" + value).getBytes(StandardCharsets.UTF_8));
        }
    }
}
