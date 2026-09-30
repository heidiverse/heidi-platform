// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.repository.ImportedTemplateRepository;
import org.heidiverse.heidi.entity.data.repository.TemplateSourceRepository;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeAttribute;
import org.heidiverse.heidi.entity.model.entity.ImportedTemplateEntity;
import org.heidiverse.heidi.entity.model.entity.LibrarySourceEntity;
import org.heidiverse.heidi.entity.model.exceptions.InvalidCredentialSchemeException;
import org.heidiverse.heidi.entity.model.exceptions.LibraryNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.TemplateNotFoundException;
import org.heidiverse.heidi.entity.model.template.I14yTemplateImportResponse;
import org.heidiverse.heidi.entity.model.template.Library;
import org.heidiverse.heidi.entity.model.template.Template;
import org.heidiverse.heidi.entity.service.utils.TemplateUtils;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateService.class);
    private static final String I14Y_SOURCE_TYPE = "I14Y_DATASET";
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final TemplateSourceRepository templateSourceRepository;
    private final ImportedTemplateRepository importedTemplateRepository;
    private final I14yService i14yService;

    public TemplateService(
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            TemplateSourceRepository templateSourceRepository,
            ImportedTemplateRepository importedTemplateRepository,
            I14yService i14yService) {
        this.objectMapper =
                objectMapper
                        .rebuild()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        this.restTemplate = restTemplate;
        this.templateSourceRepository = templateSourceRepository;
        this.importedTemplateRepository = importedTemplateRepository;
        this.i14yService = i14yService;
    }

    public List<Template> getAllTemplates() {
        List<Template> templates = getLibraries().stream()
                .flatMap(library -> library.templateUrls().stream())
                .map(this::fetchTemplateFromUrl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        templates.addAll(
                importedTemplateRepository.findAllByOrderByCreatedAtAsc().stream()
                        .map(this::readImportedTemplate)
                        .filter(Objects::nonNull)
                        .toList());
        return templates;
    }

    public Template getTemplateById(UUID id) {
        return getAllTemplates().stream()
                .filter(template -> template.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new TemplateNotFoundException("Template not found for ID: " + id));
    }

    public List<Library> getLibraries() {
        List<Library> libraries = templateSourceRepository.findAll().stream()
                .map(
                        librarySource ->
                                fetchLibraryFromUrl(
                                        librarySource.getId(), librarySource.getLibraryUrl()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        libraries.addAll(
                importedTemplateRepository.findAllByOrderByCreatedAtAsc().stream()
                        .map(this::readImportedLibrary)
                        .filter(Objects::nonNull)
                        .toList());
        return libraries;
    }

    @Transactional
    public I14yTemplateImportResponse importI14yDataset(String datasetIdentifier) {
        I14yService.Result result = i14yService.importDataset(datasetIdentifier);
        var existing =
                importedTemplateRepository.findBySourceTypeAndSourceIdAndSourceVersion(
                        I14Y_SOURCE_TYPE, result.datasetId(), result.sourceVersion());
        if (existing.isPresent()) {
            return new I14yTemplateImportResponse(
                    readImportedTemplate(existing.get()), result.warnings());
        }

        ImportedTemplateEntity importedTemplate = new ImportedTemplateEntity();
        importedTemplate.setId(result.template().id());
        importedTemplate.setSourceType(I14Y_SOURCE_TYPE);
        importedTemplate.setSourceId(result.datasetId());
        importedTemplate.setSourceVersion(result.sourceVersion());
        try {
            importedTemplate.setTemplateJson(objectMapper.writeValueAsString(result.template()));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not store the imported I14Y template.", exception);
        }
        importedTemplateRepository.save(importedTemplate);
        return new I14yTemplateImportResponse(result.template(), result.warnings());
    }

    private Template fetchTemplateFromUrl(String url) {
        try {
            String json = restTemplate.getForObject(url, String.class);
            return objectMapper.readValue(json, Template.class);
        } catch (Exception e) {
            LOGGER.error("Failed to fetch template from URL: " + url, e);
            return null;
        }
    }

    private Library fetchLibraryFromUrl(UUID libraryId, String url) {
        try {
            String json = restTemplate.getForObject(url, String.class);
            Library library = objectMapper.readValue(json, Library.class);
            return new Library(
                    libraryId, library.key(), library.displayName(), library.templateUrls());
        } catch (Exception e) {
            LOGGER.error("Failed to fetch library from URL: " + url, e);
            return null;
        }
    }

    private Template readImportedTemplate(ImportedTemplateEntity entity) {
        try {
            Template template = objectMapper.readValue(entity.getTemplateJson(), Template.class);
            if (!I14Y_SOURCE_TYPE.equals(entity.getSourceType())) {
                return template;
            }

            String libraryKey = I14yTemplateMapper.libraryKey(template.id());
            if (libraryKey.equals(template.libraryKey())) {
                return template;
            }
            return new Template(
                    template.id(),
                    template.type(),
                    template.displayName(),
                    libraryKey,
                    template.attributes(),
                    template.metaAttributes(),
                    template.style(),
                    template.issuerSettings(),
                    template.attributeRules());
        } catch (Exception exception) {
            LOGGER.error("Failed to read imported template " + entity.getId(), exception);
            return null;
        }
    }

    public void validateTemplate(UUID templateId, List<CredentialSchemeAttribute> attributes)
            throws InvalidCredentialSchemeException {
        Template template = getTemplateById(templateId);
        TemplateUtils.validateTemplate(template, attributes);
    }

    // CRUD operations for LibrarySourceEntity
    @Transactional
    public LibrarySourceEntity addLibrarySource(String libraryUrl) {
        LibrarySourceEntity librarySource = new LibrarySourceEntity();
        librarySource.setLibraryUrl(libraryUrl);
        return templateSourceRepository.save(librarySource);
    }

    @Transactional
    public LibrarySourceEntity updateLibrarySource(UUID id, String libraryUrl) {
        LibrarySourceEntity librarySource =
                templateSourceRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new LibraryNotFoundException(
                                                "LibrarySource not found for ID: " + id));
        librarySource.setLibraryUrl(libraryUrl);
        return templateSourceRepository.save(librarySource);
    }

    @Transactional
    public void deleteLibrary(UUID id) {
        if (importedTemplateRepository.existsById(id)) {
            importedTemplateRepository.deleteById(id);
            return;
        }
        if (!templateSourceRepository.existsById(id)) {
            throw new LibraryNotFoundException("LibrarySource not found for ID: " + id);
        }
        templateSourceRepository.deleteById(id);
    }

    private Library readImportedLibrary(ImportedTemplateEntity entity) {
        Template template = readImportedTemplate(entity);
        if (template == null) {
            return null;
        }
        return new Library(template.id(), template.libraryKey(), template.displayName(), List.of());
    }
}
