package com.loomai.operations.lifecycle;

import com.loomai.operations.lifecycle.LifecycleContracts.JobRequest;
import com.loomai.operations.lifecycle.LifecycleStore.EventPage;
import com.loomai.operations.lifecycle.LifecycleStore.JobPage;
import com.loomai.operations.lifecycle.LifecycleStore.JobView;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/internal/v1/deployment-operations/lifecycle", produces = MediaType.APPLICATION_JSON_VALUE)
public class LifecycleController {
    private final LifecycleService service;
    private final LifecycleStore store;
    public LifecycleController(LifecycleService service, LifecycleStore store) { this.service = service; this.store = store; }

    @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobView register(@Valid @RequestBody JobRequest request) { return service.register(request); }

    @GetMapping("/jobs")
    public JobPage list(@RequestParam(defaultValue = "0") long offset, @RequestParam(defaultValue = "50") int limit) {
        return store.list(offset, limit);
    }

    @GetMapping("/jobs/{id}")
    public JobView status(@PathVariable String id) { return store.status(id); }

    @GetMapping("/jobs/{id}/events")
    public EventPage events(@PathVariable String id, @RequestParam(defaultValue = "0") long after,
                            @RequestParam(defaultValue = "50") int limit) { return store.events(id, after, limit); }

    @PostMapping("/jobs/{id}/advance")
    public JobView advance(@PathVariable String id) { return service.advance(id); }

    @PostMapping("/jobs/{id}/retry")
    public JobView retry(@PathVariable String id) { return service.retry(id); }

    @PostMapping("/jobs/{id}/cancel")
    public JobView cancel(@PathVariable String id) { return service.cancel(id); }

    @PostMapping("/notices/dispatch")
    public Map<String, Integer> notices() { return Map.of("delivered", service.dispatchNotices()); }
}
