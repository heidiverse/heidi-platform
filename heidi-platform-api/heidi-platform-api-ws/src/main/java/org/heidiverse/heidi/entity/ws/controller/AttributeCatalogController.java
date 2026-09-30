// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import org.heidiverse.heidi.entity.model.catalog.AttributeCatalog;
import org.heidiverse.heidi.entity.model.catalog.CatalogAttribute;
import org.heidiverse.heidi.entity.service.AttributeCatalogService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/management/v1/attribute-catalogs")
public class AttributeCatalogController {

    private final AttributeCatalogService catalogService;

    public AttributeCatalogController(AttributeCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // Catalog CRUD Endpoints
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<AttributeCatalog> createCatalog(@RequestBody AttributeCatalog catalog) {
        return ResponseEntity.ok(catalogService.createCatalog(catalog));
    }

    @GetMapping
    public ResponseEntity<List<AttributeCatalog>> getAllCatalogs() {
        return ResponseEntity.ok(catalogService.getAllCatalogs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttributeCatalog> getCatalog(@PathVariable Integer id) {
        return catalogService
                .getCatalogById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<AttributeCatalog> updateCatalog(
            @PathVariable Integer id, @RequestBody AttributeCatalog catalog) {
        return ResponseEntity.ok(catalogService.updateCatalog(id, catalog));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteCatalog(@PathVariable Integer id) {
        catalogService.deleteCatalog(id);
        return ResponseEntity.noContent().build();
    }

    // Attribute CRUD Endpoints
    @PostMapping("/{catalogId}/attributes")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<CatalogAttribute> createAttribute(
            @PathVariable Integer catalogId, @RequestBody CatalogAttribute attribute) {
        return ResponseEntity.ok(catalogService.createAttribute(catalogId, attribute));
    }

    @GetMapping("/{catalogId}/attributes")
    public ResponseEntity<List<CatalogAttribute>> getAttributes(@PathVariable Integer catalogId) {
        return ResponseEntity.ok(catalogService.getAttributesByCatalogId(catalogId));
    }

    @GetMapping("/attributes/{id}")
    public ResponseEntity<CatalogAttribute> getAttribute(@PathVariable Integer id) {
        return catalogService
                .getAttributeById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/attributes/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<CatalogAttribute> updateAttribute(
            @PathVariable Integer id, @RequestBody CatalogAttribute attribute) {
        return ResponseEntity.ok(catalogService.updateAttribute(id, attribute));
    }

    @DeleteMapping("/attributes/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteAttribute(@PathVariable Integer id) {
        catalogService.deleteAttribute(id);
        return ResponseEntity.noContent().build();
    }
}
