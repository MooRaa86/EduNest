package com.example.gradproj.EduNest;

import com.example.gradproj.EduNest.testing.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole-context smoke test — the P2-01 / EB-35 acceptance proof:
 * the FULL application boots (and answers HTTP) with ZERO environment —
 * no MySQL, no DATABASE_URL/MAIL_HOST, no Docker. Everything comes from
 * the `test` profile (src/test/resources/application-test.properties).
 */
class EduNestApplicationTests extends IntegrationTestBase {

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("context loads on the fake environment alone")
	void contextLoads() {
		// context boot success IS the assertion; body intentionally empty
	}

	@Test
	@DisplayName("public route answers over HTTP (chain + MVC alive)")
	void httpLayerRespondsOnPublicRoute() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	/**
	 * The plan once said "native SQL rules out H2" — verified claim: there is exactly ONE
	 * special query in the codebase (TotalPointsRepository rank list, ROW_NUMBER() OVER).
	 * H2 2.x supports window functions; this probe proves the engine-level feature works.
	 * The full rank query gets real fixtures in P2-02.
	 */
	@Test
	@DisplayName("H2 engine supports ROW_NUMBER() OVER (the one native pattern we use)")
	void h2SupportsWindowFunctions() throws Exception {
		String sql = """
				SELECT COUNT(*) FROM (
					SELECT CAST(ROW_NUMBER() OVER (ORDER BY x) AS int) AS r
					FROM (SELECT 1 AS x UNION ALL SELECT 2) t
				) w WHERE w.r IN (1, 2)
				""";
		try (Connection c = dataSource.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql);
		     ResultSet rs = ps.executeQuery()) {
			assertTrue(rs.next());
			assertEquals(2, rs.getInt(1));
		}
	}
}
