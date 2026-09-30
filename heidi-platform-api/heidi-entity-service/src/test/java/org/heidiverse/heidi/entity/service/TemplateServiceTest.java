// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.entity.data.repository.ImportedTemplateRepository;
import org.heidiverse.heidi.entity.data.repository.TemplateSourceRepository;
import org.heidiverse.heidi.entity.model.entity.ImportedTemplateEntity;
import org.heidiverse.heidi.entity.model.template.Template;
import org.heidiverse.heidi.entity.model.template.TemplateType;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class TemplateServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listsImportedSchemaByTitle() throws Exception {
        TemplateSourceRepository sourceRepository = mock(TemplateSourceRepository.class);
        ImportedTemplateRepository importedRepository = mock(ImportedTemplateRepository.class);
        TemplateService service = service(sourceRepository, importedRepository);
        UUID templateId = UUID.randomUUID();
        ImportedTemplateEntity imported = imported(templateId, "Resident card");
        when(sourceRepository.findAll()).thenReturn(List.of());
        when(importedRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(imported));

        var libraries = service.getLibraries();

        assertEquals(1, libraries.size());
        assertEquals(templateId, libraries.getFirst().id());
        assertEquals("Resident card", libraries.getFirst().displayName());
        assertEquals("i14y:" + templateId, libraries.getFirst().key());
    }

    @Test
    void deletesImportedSchema() {
        TemplateSourceRepository sourceRepository = mock(TemplateSourceRepository.class);
        ImportedTemplateRepository importedRepository = mock(ImportedTemplateRepository.class);
        TemplateService service = service(sourceRepository, importedRepository);
        UUID templateId = UUID.randomUUID();
        when(importedRepository.existsById(templateId)).thenReturn(true);

        service.deleteLibrary(templateId);

        verify(importedRepository).deleteById(templateId);
        verifyNoInteractions(sourceRepository);
    }

    private static TemplateService service(
            TemplateSourceRepository sourceRepository,
            ImportedTemplateRepository importedRepository) {
        return new TemplateService(
                MAPPER,
                new RestTemplate(),
                sourceRepository,
                importedRepository,
                mock(I14yService.class));
    }

    private static ImportedTemplateEntity imported(UUID id, String title) throws Exception {
        Template template = new Template(
                id,
                TemplateType.CredentialSchemaTemplate,
                title,
                "i14y:" + id,
                List.of(),
                List.of(),
                null,
                new Template.IssuerSettings("SOFTWARE_NO_AUTH", "", "", "", null),
                Map.of());
        ImportedTemplateEntity entity = new ImportedTemplateEntity();
        entity.setId(id);
        entity.setSourceType("I14Y_DATASET");
        entity.setSourceId(UUID.randomUUID());
        entity.setSourceVersion("1.0.0");
        entity.setTemplateJson(MAPPER.writeValueAsString(template));
        return entity;
    }
}
