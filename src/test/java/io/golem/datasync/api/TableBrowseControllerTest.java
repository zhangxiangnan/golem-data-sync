package io.golem.datasync.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.golem.datasync.api.ApiModels.TableRowsResponse;
import io.golem.datasync.service.DataSourceService;
import io.golem.datasync.service.TableBrowseService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TableBrowseControllerTest {
    @Test
    void defaultsPaginationAndPreservesNullAndStringCells() throws Exception {
        var browse = mock(TableBrowseService.class);
        when(browse.rows("s", "orders", 1, 50)).thenReturn(new TableRowsResponse(
                List.of(), List.of(Arrays.asList("9223372036854775807", null, "")), 1, 1, 50, true));
        var mvc = MockMvcBuilders.standaloneSetup(new DataSourceController(mock(DataSourceService.class), browse))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/data-sources/s/tables/orders/rows"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0][0]").value("9223372036854775807"))
                .andExpect(jsonPath("$.rows[0][1]").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rows[0][2]").value(""))
                .andExpect(jsonPath("$.pageSize").value(50));
        mvc.perform(get("/api/data-sources/s/tables/orders/rows?page=abc")).andExpect(status().isBadRequest());
        when(browse.rows("s", "missing", 1, 50)).thenThrow(new ResourceNotFoundException("表不存在"));
        mvc.perform(get("/api/data-sources/s/tables/missing/rows")).andExpect(status().isNotFound());
        when(browse.rows("s", "orders", 0, 50)).thenThrow(new RequestValidationException("非法页码"));
        mvc.perform(get("/api/data-sources/s/tables/orders/rows?page=0")).andExpect(status().isBadRequest());
    }
}
