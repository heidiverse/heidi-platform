// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import jakarta.validation.Valid;

import org.heidiverse.heidi.entity.model.entity.LibrarySourceEntity;
import org.heidiverse.heidi.entity.model.template.Library;
import org.heidiverse.heidi.entity.model.template.I14yTemplateImportRequest;
import org.heidiverse.heidi.entity.model.template.I14yTemplateImportResponse;
import org.heidiverse.heidi.entity.model.template.Template;
import org.heidiverse.heidi.entity.service.TemplateService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/management/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<Template>> listTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Template> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @GetMapping("/libraries")
    public ResponseEntity<List<Library>> listLibraries() {
        return ResponseEntity.ok(templateService.getLibraries());
    }

    @PostMapping("/i14y")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<I14yTemplateImportResponse> importI14yTemplate(
            @RequestBody @Valid I14yTemplateImportRequest request) {
        return ResponseEntity.ok(templateService.importI14yDataset(request.datasetId()));
    }

    // CRUD endpoints for LibrarySourceEntity

    @PostMapping("/libraries")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<LibrarySourceEntity> addLibrarySource(@RequestParam String libraryUrl) {
        return ResponseEntity.ok(templateService.addLibrarySource(libraryUrl));
    }

    @PutMapping("/libraries/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<LibrarySourceEntity> updateLibrarySource(
            @PathVariable UUID id, @RequestParam String libraryUrl) {
        return ResponseEntity.ok(templateService.updateLibrarySource(id, libraryUrl));
    }

    @DeleteMapping("/libraries/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteLibrarySource(@PathVariable UUID id) {
        templateService.deleteLibrary(id);
        return ResponseEntity.noContent().build();
    }
}
