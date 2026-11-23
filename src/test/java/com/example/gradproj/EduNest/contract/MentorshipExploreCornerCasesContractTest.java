package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.mentorShip.DifficultyLevel;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-06 / EB-59 / MSH-12: Contract test verifying that GET /api/v1/mentorship/explore
 * handles every combination of optional parameters (keyword, category, minPrice, maxPrice)
 * and all corner cases without failing or causing SQL transaction aborts (25P02 / 42P18).
 */
@Tag("critical")
class MentorshipExploreCornerCasesContractTest extends ProdChainIntegrationTestBase {

    @Autowired private AuthFixture auth;
    @Autowired private MentorShipRepository mentorshipRepository;
    @Autowired private MentorRepository mentorRepository;

    private static final String EXPLORE = "/api/v1/mentorship/explore";
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        Mentor activeMentor = auth.mentor("active-mentor-" + suffix + "@example.org");
        Mentor deletedMentor = auth.mentor("deleted-mentor-" + suffix + "@example.org");
        deletedMentor.setDeleted(true);
        mentorRepository.saveAndFlush(deletedMentor);

        // 1. Active Backend course: base price 100, discount 20% -> effective price = 80.0
        mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("Java Spring Pro " + suffix)
                .subtitle("Master Spring Boot " + suffix)
                .description("In-depth Spring backend course " + suffix)
                .category("backend")
                .difficultyLevel(DifficultyLevel.INTERMEDIATE)
                .status(Status.ACTIVE)
                .price(100.0)
                .discountPercentage(20)
                .duration(10.0)
                .mentor(activeMentor)
                .build());

        // 2. Active Frontend course: base price 50, discount 0% -> effective price = 50.0
        mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("React Modern Guide " + suffix)
                .subtitle("Frontend React Hooks " + suffix)
                .description("Build beautiful UI with React " + suffix)
                .category("frontend")
                .difficultyLevel(DifficultyLevel.BEGINNER)
                .status(Status.ACTIVE)
                .price(50.0)
                .discountPercentage(0)
                .duration(8.0)
                .mentor(activeMentor)
                .build());

        // 3. Active Free course: base price 0.0 -> effective price = 0.0
        mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("Free Intro to Coding " + suffix)
                .subtitle("Basic fundamentals " + suffix)
                .description("Getting started free " + suffix)
                .category("general")
                .difficultyLevel(DifficultyLevel.ALL_LEVEL)
                .status(Status.ACTIVE)
                .price(0.0)
                .discountPercentage(0)
                .duration(2.0)
                .mentor(activeMentor)
                .build());

        // 4. Draft course (must NEVER appear in explore)
        mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("Draft Secret Course " + suffix)
                .subtitle("Hidden draft " + suffix)
                .description("Not published " + suffix)
                .category("backend")
                .difficultyLevel(DifficultyLevel.EXPERT)
                .status(Status.DRAFT)
                .price(200.0)
                .discountPercentage(0)
                .duration(5.0)
                .mentor(activeMentor)
                .build());

        // 5. Course with deleted mentor (must NEVER appear in explore)
        mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("Deleted Mentor Course " + suffix)
                .subtitle("Orphan course " + suffix)
                .description("Mentor deleted " + suffix)
                .category("backend")
                .difficultyLevel(DifficultyLevel.BEGINNER)
                .status(Status.ACTIVE)
                .price(30.0)
                .discountPercentage(0)
                .duration(3.0)
                .mentor(deletedMentor)
                .build());
    }

    private long getTotalElements(String query) throws Exception {
        var result = mockMvc.perform(get(EXPLORE + query))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.apiResponse.mentorShips.totalElements")).longValue();
    }

    @Test
    @DisplayName("Corner Case 1: No query parameters -> 200 OK, returns active mentorships")
    void noParams_succeeds200() throws Exception {
        mockMvc.perform(get(EXPLORE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorShips.content").isArray())
                .andExpect(jsonPath("$.apiResponse.mentorShips.totalElements").isNumber());
    }

    @Test
    @DisplayName("Corner Case 2: Keyword only filter -> isolates matching active row")
    void keywordOnly_filtersCorrectly() throws Exception {
        assertThat(getTotalElements("?keyword=Java Spring Pro " + suffix)).isEqualTo(1);
        assertThat(getTotalElements("?keyword=React Modern Guide " + suffix)).isEqualTo(1);
        assertThat(getTotalElements("?keyword=NonexistentToken_" + suffix)).isEqualTo(0);
    }

    @Test
    @DisplayName("Corner Case 3: Blank/whitespace keyword -> ignored, returns all active candidates")
    void whitespaceKeyword_treatedAsNoKeyword() throws Exception {
        long totalWithWhitespace = getTotalElements("?keyword=   ");
        long totalWithNoParams = getTotalElements("");
        assertThat(totalWithWhitespace).isEqualTo(totalWithNoParams);
    }

    @Test
    @DisplayName("Corner Case 4: Category only filter -> case-insensitive match")
    void categoryOnly_caseInsensitive() throws Exception {
        assertThat(getTotalElements("?keyword=" + suffix + "&category=backend")).isEqualTo(1);
        assertThat(getTotalElements("?keyword=" + suffix + "&category=BACKEND")).isEqualTo(1);
        assertThat(getTotalElements("?keyword=" + suffix + "&category=frontend")).isEqualTo(1);
        assertThat(getTotalElements("?keyword=" + suffix + "&category=FRONTEND")).isEqualTo(1);
        assertThat(getTotalElements("?keyword=" + suffix + "&category=nonexistent")).isEqualTo(0);
    }

    @Test
    @DisplayName("Corner Case 5: Blank category -> ignored, does not filter out results")
    void whitespaceCategory_treatedAsNoCategory() throws Exception {
        assertThat(getTotalElements("?keyword=" + suffix + "&category=   ")).isEqualTo(3);
    }

    @Test
    @DisplayName("Corner Case 6: MinPrice only filter -> evaluates effective discounted price")
    void minPriceOnly_evaluatesEffectivePrice() throws Exception {
        // Effective prices: Free = 0.0, React = 50.0, Java = 80.0
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=60.0")).isEqualTo(1); // Java (80.0) only
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=50.0")).isEqualTo(2); // React (50.0) and Java (80.0)
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=0.0")).isEqualTo(3);  // All 3
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=-10.0")).isEqualTo(3); // Negative price doesn't fail
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=9999.0")).isEqualTo(0);
    }

    @Test
    @DisplayName("Corner Case 7: MaxPrice only filter -> evaluates effective discounted price")
    void maxPriceOnly_evaluatesEffectivePrice() throws Exception {
        // Effective prices: Free = 0.0, React = 50.0, Java = 80.0
        assertThat(getTotalElements("?keyword=" + suffix + "&maxPrice=0.0")).isEqualTo(1);  // Free course only
        assertThat(getTotalElements("?keyword=" + suffix + "&maxPrice=50.0")).isEqualTo(2); // Free and React
        assertThat(getTotalElements("?keyword=" + suffix + "&maxPrice=100.0")).isEqualTo(3); // All 3
        assertThat(getTotalElements("?keyword=" + suffix + "&maxPrice=-10.0")).isEqualTo(0); // 200 OK with 0 results
    }

    @Test
    @DisplayName("Corner Case 8: Price range (minPrice + maxPrice) -> bounded range matching")
    void priceRange_inclusive() throws Exception {
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=40.0&maxPrice=90.0")).isEqualTo(2); // React (50) + Java (80)
        assertThat(getTotalElements("?keyword=" + suffix + "&minPrice=0.0&maxPrice=60.0")).isEqualTo(2);  // Free (0) + React (50)
    }

    @Test
    @DisplayName("Corner Case 9: Inverted price range (minPrice > maxPrice) -> returns 0 results, 200 OK")
    void invertedPriceRange_returnsEmptyList200() throws Exception {
        mockMvc.perform(get(EXPLORE).queryParam("minPrice", "100.0").queryParam("maxPrice", "50.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorShips.content").isEmpty())
                .andExpect(jsonPath("$.apiResponse.mentorShips.totalElements").value(0));
    }

    @Test
    @DisplayName("Corner Case 10: All 4 filters combined -> applies conjunction cleanly")
    void allFourFilters_combined() throws Exception {
        // Java Spring Pro: keyword="Spring Pro <suffix>", category="backend", minPrice=70, maxPrice=90
        assertThat(getTotalElements("?keyword=Spring Pro " + suffix + "&category=backend&minPrice=70.0&maxPrice=90.0"))
                .isEqualTo(1);

        // Mismatched category for React course
        assertThat(getTotalElements("?keyword=React Modern Guide " + suffix + "&category=backend&minPrice=10.0&maxPrice=100.0"))
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Corner Case 11: Draft and deleted mentor courses are excluded from explore")
    void confinement_draftAndDeletedMentorExcluded() throws Exception {
        // Total matching suffix must be exactly the 3 ACTIVE courses with active mentor
        assertThat(getTotalElements("?keyword=" + suffix)).isEqualTo(3);
    }

    @Test
    @DisplayName("Corner Case 12: Pagination edge cases clamped gracefully")
    void paginationClamping_handledGracefully() throws Exception {
        mockMvc.perform(get(EXPLORE).param("page", "-1").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorShips.page").value(0))
                .andExpect(jsonPath("$.apiResponse.mentorShips.size").value(1));

        mockMvc.perform(get(EXPLORE).param("page", "0").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorShips.size").value(50));
    }
}
