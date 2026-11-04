package com.ai.fabric.platform.backend.documentstorage.web;

import com.ai.fabric.platform.backend.documentstorage.model.DocumentStorageContracts.*;
import com.ai.fabric.platform.backend.documentstorage.service.CustomerDocumentStorageService;
import com.ai.fabric.platform.backend.documentstorage.service.DocumentStorageSelectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/document-storage/connections")
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class CustomerDocumentStorageController {
    private final CustomerDocumentStorageService storage;
    private final DocumentStorageSelectionService selections;
    public CustomerDocumentStorageController(CustomerDocumentStorageService storage,DocumentStorageSelectionService selections) { this.storage=storage;this.selections=selections; }
    @GetMapping public Catalogue list(@PathVariable String deploymentId) { return storage.list(deploymentId); }
    @PostMapping public Connection create(@PathVariable String deploymentId,@Valid @RequestBody Create request) { return storage.create(deploymentId,request); }
    @PostMapping("/{connectionId}/prepare") public Connection prepare(@PathVariable String deploymentId,@PathVariable String connectionId,@Valid @RequestBody Command request) { return storage.prepare(deploymentId,connectionId,request); }
    @PostMapping("/{connectionId}/bind") public Binding bind(@PathVariable String deploymentId,@PathVariable String connectionId,@Valid @RequestBody Bind request) { return storage.bind(deploymentId,connectionId,request); }
    @GetMapping("/{connectionId}/uploads") public List<Upload> uploads(@PathVariable String deploymentId,@PathVariable String connectionId) { return storage.uploads(deploymentId,connectionId); }
    @PostMapping(value="/{connectionId}/uploads",consumes="application/octet-stream")
    public Upload upload(@PathVariable String deploymentId,@PathVariable String connectionId,
        @RequestParam String filename,@RequestParam(defaultValue="public/") String prefix,@RequestParam String operationKey,HttpServletRequest request) throws IOException {
        storage.requireConnection(deploymentId,connectionId);
        if(request.getContentLengthLong()>CustomerDocumentStorageService.MAX_UPLOAD_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Document exceeds the 1 MiB source limit.");
        byte[] bytes=request.getInputStream().readNBytes(CustomerDocumentStorageService.MAX_UPLOAD_BYTES+1);
        if(bytes.length>CustomerDocumentStorageService.MAX_UPLOAD_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Document exceeds the 1 MiB source limit.");
        return storage.upload(deploymentId,connectionId,filename,prefix,operationKey,bytes);
    }
    @PostMapping("/{connectionId}/prepare-selection") public SelectionResult selection(@PathVariable String deploymentId,@PathVariable String connectionId,@Valid @RequestBody PrepareSelection request) { return selections.prepare(deploymentId,connectionId,request); }
    @GetMapping("/{connectionId}/selections") public List<SelectionItem> selections(@PathVariable String deploymentId,@PathVariable String connectionId,@RequestParam String targetProfileId,@RequestParam String releaseId) { return selections.list(deploymentId,connectionId,targetProfileId,releaseId); }
}
