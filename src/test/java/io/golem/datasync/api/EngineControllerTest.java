package io.golem.datasync.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.golem.datasync.DataSyncApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = DataSyncApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EngineControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesRunnableProfilesAndDisabledFlink() throws Exception {
        mvc.perform(get("/api/engine-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("zeta-local")))
                .andExpect(jsonPath("$[*].id", hasItem("spark-local-mock")))
                .andExpect(jsonPath("$[?(@.engineType == 'FLINK')].enabled").value(hasItem(false)));
    }

    @Test
    void exposesEngineSpecificLimitations() throws Exception {
        mvc.perform(get("/api/engine-capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.engineType == 'ZETA')].supported[0]").exists())
                .andExpect(jsonPath("$[?(@.engineType == 'FLINK')].configured").value(hasItem(false)));
    }
}
