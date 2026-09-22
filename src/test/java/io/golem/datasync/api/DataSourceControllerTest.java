package io.golem.datasync.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.golem.datasync.DataSyncApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = DataSyncApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataSourceControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void createsListsAndDeletesWithoutReturningPassword() throws Exception {
        String body = """
                {"name":"source","host":"127.0.0.1","port":3306,"database":"source_db","username":"sync","password":"secret"}
                """;
        String response = mvc.perform(post("/api/data-sources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordConfigured").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asText();
        mvc.perform(get("/api/data-sources"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/data-sources/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void rejectsUnsafeDatabaseIdentifier() throws Exception {
        mvc.perform(post("/api/data-sources").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"bad","host":"127.0.0.1","port":3306,"database":"a;drop table x","username":"root","password":""}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }
}
