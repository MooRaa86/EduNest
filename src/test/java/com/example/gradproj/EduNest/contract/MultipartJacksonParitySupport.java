package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.service.projects.ProjectServiceImpl;
import com.example.gradproj.EduNest.service.tasks.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Characterizes the existing direct-mapper create and wrapped PATCH error paths. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
abstract class MultipartJacksonParitySupport {
    @Autowired MockMvc mockMvc;
    @Autowired AuthFixture auth;
    @MockBean ProjectServiceImpl projects;
    @MockBean TaskService tasks;

    private String authorization() {
        String email = "eb63-multipart-" + UUID.randomUUID() + "@example.org";
        auth.mentor(email);
        return auth.authToken(email, AuthFixture.MENTOR);
    }

    private MockMultipartFile json(String value) {
        return new MockMultipartFile("req", "", "application/json", value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createUnknownFieldsReturnExistingUnknownFieldError() throws Exception {
        String token = authorization();
        for (String route : new String[]{"/api/v1/project", "/api/v1/task"}) {
            mockMvc.perform(multipart(route).file(json("{\"unexpected\":true}"))
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ERR_UNKNOWN_FIELD"))
                    .andExpect(jsonPath("$.fields.invalidField").value("unexpected"));
        }
    }

    @Test
    void patchUnknownFieldsKeepExistingSanitizedCatchAll() throws Exception {
        String token = authorization();
        for (String route : new String[]{"/api/v1/project/", "/api/v1/task/"}) {
            mockMvc.perform(multipart(route + UUID.randomUUID()).file(json("{\"unexpected\":true}"))
                            .with(request -> { request.setMethod("PATCH"); return request; })
                            .header("Authorization", token))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("ERR_INTERNAL_SYSTEM"))
                    .andExpect(jsonPath("$.refId").exists());
        }
    }

    @Test
    void validJsonStillReachesCreateAndPatchServices() throws Exception {
        String token = authorization();
        for (String route : new String[]{"/api/v1/project", "/api/v1/task"}) {
            mockMvc.perform(multipart(route).file(json("{}"))
                            .header("Authorization", token))
                    .andExpect(status().isCreated());
            mockMvc.perform(multipart(route + "/" + UUID.randomUUID()).file(json("{}"))
                            .with(request -> { request.setMethod("PATCH"); return request; })
                            .header("Authorization", token))
                    .andExpect(status().isOk());
        }
    }
}
