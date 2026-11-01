package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-05 / EB-3 — V-03: explore keyword wildcard POC (MSH-12).
 *
 * Audit ({@code mds/ISSUES.md} MSH-12): {@code MentorShipRepository#searchMentorShips}
 * (L96-126) binds {@code :keyword} RAW into {@code LOWER(CONCAT('%', :keyword, '%'))}
 * across 5 columns (title / description / subtitle / category / mentor name) —
 * no {@code ESCAPE} clause, no pre-escaping. {@code GET
 * /api/v1/mentorship/explore} is PUBLIC (PublicRoutesPolicy), so any ANONYMOUS
 * visitor can submit {@code %}- / {@code _}-only keywords.
 *
 * POC mechanics (Option A doctrine — pinning TODAY's behavior, verdict flips
 * when P4-10 escapes the keyword): the real prod chain runs end-to-end over the
 * shared H2 context. Suffix-tokenized titles make each assertion
 * context-sharing-proof (other probe suites seed the same DB). Latency is
 * PRINTED per request ({@code [V-03]} lines) — H2 in-memory timings are noisy,
 * so the row-scan effect is proven by RESULT-SET size (every candidate row
 * matched) while the printed latency lines remain the recorded evidence note.
 *
 * Transport note: keywords ride {@code queryParam(...)} — a direct parameter-
 * map injection — so a raw {@code %}/{@code _} lands in {@code :keyword}
 * verbatim; the {@code param-received} echo recorded in every {@code [V-03]}
 * line proves that journey (an inline percent-encoded query string did NOT
 * deliver the encoded keyword correctly on the first run).
 *
 * Expected verdict (CONFIRMED): every wildcard-only keyword returns the FULL
 * candidate set (keyword semantics destroyed — match all), and a {@code %}/
 * {@code _} inside an otherwise-literal keyword silently widens the match to
 * rows that never contain that literal. Canonical abusive payload list for
 * P4-10 (escape {@code %} and {@code _} with the LIKE {@code ESCAPE} clause):
 * {@code %} {@code _} {@code %%} {@code __} {@code %_} {@code _%} {@code %a}
 * {@code a%} {@code _a} {@code a_} {@code 50%} (any {@code %}-bearing literal).
 *
 * Scope note (matter of record for P4-10): the status/mentor predicates are
 * SEPARATE from the keyword OR-chain, so wildcards explode the match ONLY
 * within the {@code status=ACTIVE AND mentor.deleted=false} candidate set —
 * the DRAFT row seeded here must NEVER leak. Escape-only (Small) sizes the
 * P4-10 fix; a dedicated full-text approach is NOT forced by this evidence.
 */
@Tag("critical")
class MentorshipExploreWildcardPocTest extends ProdChainIntegrationTestBase {

	@Autowired private AuthFixture auth;
	@Autowired private MentorShipRepository mentorships;

	private static final String MENTOR_EMAIL = "probe-eb3-v03-mentor@example.org";
	/** Canonical wildcard-only payloads that expand to match-all (asserted exactly). */
	private static final String[] EXPAND_ALL_PAYLOADS = { "%", "%%", "%_", "_%", "__" };

	private Mentor mentorProbe;

	/**
	 * One mentor + 10 ACTIVE + 1 DRAFT, every title suffix-tokenized so the
	 * shared-context DB can never collide with another suite's seeds:
	 * <ul>
	 *   <li>1 control row {@code zephyrtronics-<suffix>} (unique literal).</li>
	 *   <li>6 plain rows ({@code plain-#i}) so the wildcard total visibly
	 *       out-sizes the control.</li>
	 *   <li>{@code Summer%Off} + {@code SummerXOff} pair ({@code %} semantics).</li>
	 *   <li>{@code Fundamentals_101} + {@code FundamentalsX101} pair ({@code _} semantics).</li>
	 *   <li>1 DRAFT row that must NEVER leak, even under match-all keywords.</li>
	 * </ul>
	 */
	private void seed(String suffix) {
		this.mentorProbe = auth.mentor(MENTOR_EMAIL);

		saveActive(suffix, Status.ACTIVE, "eb3-zephyrtronics-" + suffix); // control row
		for (int i = 0; i < 6; i++) {
			saveActive(suffix, Status.ACTIVE, "eb3-plain-" + i + "-" + suffix);
		}
		saveActive(suffix, Status.ACTIVE, "eb3-Summer%Off-" + suffix);
		saveActive(suffix, Status.ACTIVE, "eb3-SummerXOff-" + suffix);
		saveActive(suffix, Status.ACTIVE, "eb3-Fundamentals_101-" + suffix);
		saveActive(suffix, Status.ACTIVE, "eb3-FundamentalsX101-" + suffix);

		mentorships.saveAndFlush(MentorShip.builder()
				.title("eb3-draft-" + suffix).subtitle("probe").description("probe")
				.category("backEnd").price(0.0).duration(1.0).status(Status.DRAFT)
				.mentor(mentorProbe).build());

		// sanity: the fixture seeded all 10 ACTIVE rows as exclusive suffix tokens
		assertThat(mentorships.findAll().stream().filter(m -> m.getTitle() != null
				&& m.getTitle().contains(suffix)).count()).as("seeded tokenized rows")
				.isGreaterThanOrEqualTo(11);
	}

	private void saveActive(String suffix, Status status, String title) {
		mentorships.saveAndFlush(MentorShip.builder()
				.title(title).subtitle("probe").description("probe")
				.category("backEnd").price(0.0).duration(1.0).status(status)
				.mentor(mentorProbe).build());
	}

	/** Total candidate matches for one keyword (PUBLIC route — no auth header). */
	private long exploreTotal(String keyword) throws Exception {
		long start = System.nanoTime();
		var result = mockMvc.perform(get("/api/v1/mentorship/explore").queryParam("keyword", keyword))
				.andExpect(status().isOk())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		long total = ((Number) JsonPath.read(body, "$.apiResponse.mentorShips.totalElements")).longValue();
		System.out.printf("[V-03] keyword='%s' param-received='%s' totalElements=%d latency=%d ms%n",
				keyword, result.getRequest().getParameter("keyword"), total,
				(System.nanoTime() - start) / 1_000_000);
		return total;
	}

	/** Sweep ALL explore pages for a keyword; returns the first-page totalElements. */
	private long sweepAll(String keyword, StringBuilder combined) throws Exception {
		long start = System.nanoTime();
		long total = -1;
		String received = null;
		int page = 0;
		while (true) {
			var result = mockMvc.perform(get("/api/v1/mentorship/explore")
							.queryParam("keyword", keyword)
							.queryParam("page", String.valueOf(page))
							.queryParam("size", "50"))
					.andExpect(status().isOk())
					.andReturn();
			String body = result.getResponse().getContentAsString();
			combined.append(body);
			if (total < 0) {
				total = ((Number) JsonPath.read(body, "$.apiResponse.mentorShips.totalElements")).longValue();
				received = result.getRequest().getParameter("keyword");
			}
			int totalPages = JsonPath.read(body, "$.apiResponse.mentorShips.totalPages");
			if (++page >= totalPages) break;
		}
		System.out.printf("[V-03] sweep keyword='%s' param-received='%s' totalElements=%d latency=%d ms (all pages)%n",
				keyword, received, total, (System.nanoTime() - start) / 1_000_000);
		return total;
	}

	/**
	 * V-03 CONFIRMED — wildcard-only keywords match EVERYTHING: the keyword is
	 * destroyed as a selector and the query matches the full ACTIVE candidate
	 * set (a scan over all 5 LIKE columns) from an ANONYMOUS request. The DRAFT
	 * row stays confined even under match-all (the status predicate is a
	 * separate AND-term — wildcards scope to the candidate set, not the table).
	 * Contrast: a truly-unique literal keyword matches exactly its own row.
	 */
	@Test
	@DisplayName("V-03: %-only / _-only PUBLIC keywords match every candidate (row-scan) — draft stays confined")
	void v03_wildcardKeywordsMatchEverything() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		seed(suffix);
		String control = "eb3-zephyrtronics-" + suffix;

		// CONTROL: unique literal keyword → exactly its own row
		assertThat(exploreTotal(control)).as("unique literal keyword isolates its row").isEqualTo(1);

		// PAYLOADS: every wildcard-only keyword matches every seeded ACTIVE row
		String[] activeTokens = { "eb3-zephyrtronics-" + suffix,
				"eb3-plain-0-" + suffix, "eb3-plain-1-" + suffix, "eb3-plain-2-" + suffix,
				"eb3-plain-3-" + suffix, "eb3-plain-4-" + suffix, "eb3-plain-5-" + suffix,
				"eb3-Summer%Off-" + suffix, "eb3-SummerXOff-" + suffix,
				"eb3-Fundamentals_101-" + suffix, "eb3-FundamentalsX101-" + suffix };

		for (String payload : EXPAND_ALL_PAYLOADS) {
			StringBuilder combined = new StringBuilder();
			long total = sweepAll(payload, combined);
			assertThat(total).as("wildcard payload '%s' widens to the full candidate set", payload)
					.isGreaterThanOrEqualTo(activeTokens.length);
			for (String token : activeTokens) {
				assertThat(combined)
						.as("match-all sweep must include '%s' (keyword is no selector)", token)
						.contains(token);
			}
			assertThat(combined)
					.as("DRAFT must stay confined even under match-all keyword '%s'", payload)
					.doesNotContain("eb3-draft-" + suffix);
		}
	}

	/**
	 * V-03 CONFIRMED — %/_ inside an otherwise-literal keyword silently WIDEN
	 * the match to rows that never contain the literal: "Summer%Off" is
	 * impossible to isolate (its % is a wildcard that matches "SummerXOff"
	 * too), while the only-%-free sibling searches exactly. Same story for
	 * "Fundamentals_101" vs "FundamentalsX101" (underscore = any single char).
	 */
	@Test
	@DisplayName("V-03: % / _ inside a literal keyword alter semantics — siblings impossible to isolate")
	void v03_percentAndUnderscoreAlterSemantics() throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		seed(suffix);

		// literal, wildcard-free searches stay exact (both siblings isolatable)
		assertThat(exploreTotal("eb3-summerxoff-" + suffix))
				.as("literal-only keyword finds its one row").isEqualTo(1);
		assertThat(exploreTotal("eb3-fundamentalsx101-" + suffix))
				.as("literal-only keyword finds its one row").isEqualTo(1);

		// but a keyword carrying % / _ cannot isolate its literal target:
		// "Summer%Off" search ALSO returns "SummerXOff" (== 2 rows)
		assertThat(exploreTotal("eb3-summer%off-" + suffix))
				.as("'%' inside keyword widens to both siblings — semantics altered")
				.isEqualTo(2);
		assertThat(exploreTotal("eb3-fundamentals_101-" + suffix))
				.as("'_' inside keyword widens to both siblings — semantics altered")
				.isEqualTo(2);
	}
}
