package com.omnia.backend.controller;
import com.omnia.backend.dto.request.CompanyEditorRequest;
import com.omnia.backend.dto.response.CompanyEditorResponse;
import com.omnia.backend.service.impl.CompanyEditorService;
import com.omnia.backend.service.interfaces.FileStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/organizations") @Validated
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class CompanyEditorController {
    private final CompanyEditorService service;private final FileStorageService storage;
    public CompanyEditorController(CompanyEditorService service,FileStorageService storage){this.service=service;this.storage=storage;}
    @GetMapping("/editor/capabilities") public Map<String,Integer> capabilities(){return Map.of("version",1);}
    @GetMapping("/editor/administrators") public List<CompanyEditorResponse.Administrator> administrators(@RequestParam(defaultValue="")String query){return service.candidates(query);}
    @GetMapping("/{id}/editor") public CompanyEditorResponse get(@PathVariable @Positive Long id){return service.get(id);}
    @PostMapping("/editor") public ResponseEntity<CompanyEditorResponse> create(@Valid @RequestBody CompanyEditorRequest request){return ResponseEntity.status(201).body(service.save(null,request));}
    @PutMapping("/{id}/editor") public CompanyEditorResponse update(@PathVariable @Positive Long id,@Valid @RequestBody CompanyEditorRequest request){return service.save(id,request);}
    @PostMapping(value="/editor/logo",consumes="multipart/form-data") public Map<String,Object> logo(@RequestParam("file")MultipartFile file){
        if(file.isEmpty()||file.getSize()>5L*1024*1024||(file.getContentType()==null||!Set.of("image/png","image/jpeg").contains(file.getContentType())))
            throw new IllegalArgumentException("Logo must be PNG/JPG up to 5 MB");
        // Storage validates actual image signatures, declared MIME and extension before accepting bytes.
        var uploaded=storage.storeFile(file);return Map.of("id",uploaded.getId(),"logoUrl","/api/files/"+uploaded.getId());
    }
}
