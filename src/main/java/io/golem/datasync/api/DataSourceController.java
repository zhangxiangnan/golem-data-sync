package io.golem.datasync.api;

import io.golem.datasync.api.ApiModels.ColumnInfo;
import io.golem.datasync.api.ApiModels.ConnectionTestResponse;
import io.golem.datasync.api.ApiModels.DataSourceRequest;
import io.golem.datasync.api.ApiModels.DataSourceResponse;
import io.golem.datasync.api.ApiModels.TableInfo;
import io.golem.datasync.service.DataSourceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {
    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<DataSourceResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public DataSourceResponse get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceResponse create(@Valid @RequestBody DataSourceRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public DataSourceResponse update(@PathVariable String id, @Valid @RequestBody DataSourceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { service.delete(id); }

    @PostMapping("/{id}/test")
    public ConnectionTestResponse test(@PathVariable String id) { return service.test(id); }

    @GetMapping("/{id}/tables")
    public List<TableInfo> tables(@PathVariable String id) { return service.tables(id); }

    @GetMapping("/{id}/tables/{table}/columns")
    public List<ColumnInfo> columns(@PathVariable String id, @PathVariable String table) {
        return service.columns(id, table);
    }
}
