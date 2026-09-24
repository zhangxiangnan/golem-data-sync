package io.golem.datasync.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lab")
public class LabController {
    private final LabService service;private final LabCatalog catalog;
    public LabController(LabService service,LabCatalog catalog){this.service=service;this.catalog=catalog;}
    @GetMapping("/catalog") public ObjectNode catalog(){return service.catalogDocument();}
    @GetMapping("/cluster") public ObjectNode cluster(){return service.cluster();}
    @GetMapping("/experiments") public List<ObjectNode> list(){return service.experiments();}
    @PostMapping("/experiments") public ObjectNode create(@RequestBody JsonNode body){return service.save(null,body);}
    @GetMapping("/experiments/{id}") public ObjectNode get(@PathVariable String id){return service.experiment(id);}
    @PutMapping("/experiments/{id}") public ObjectNode update(@PathVariable String id,@RequestBody JsonNode body){return service.save(id,body);}
    @DeleteMapping("/experiments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id){service.delete(id);}
    @PostMapping("/experiments/{id}/copy") public ObjectNode copy(@PathVariable String id){return service.copy(id);}
    @PostMapping("/experiments/{id}/validate") public ObjectNode validate(@PathVariable String id,@RequestParam(defaultValue="true") boolean preflight){return service.validate(id,preflight);}
    @PostMapping("/experiments/{id}/runs") public ObjectNode start(@PathVariable String id){return service.start(id);}
    @GetMapping("/runs") public List<ObjectNode> runs(@RequestParam(required=false) String experimentId){return service.runs(experimentId);}
    @GetMapping("/runs/{id}") public ObjectNode run(@PathVariable String id){return service.detail(id);}
    @GetMapping("/runs/{id}/metrics") public List<JsonNode> metrics(@PathVariable String id){return service.metrics(id);}
    @GetMapping("/runs/{id}/logs") public ObjectNode logs(@PathVariable String id){return service.logs(id);}
    @PostMapping("/runs/{id}/cancel") public ObjectNode cancel(@PathVariable String id){return service.stop(id,false);}
    @PostMapping("/runs/{id}/savepoint") public ObjectNode savepoint(@PathVariable String id){return service.stop(id,true);}
    @PostMapping("/runs/{id}/restore") public ObjectNode restore(@PathVariable String id){return service.restore(id);}
    @GetMapping("/compare") public List<ObjectNode> compare(@RequestParam List<String> ids){return service.compare(ids);}
}
