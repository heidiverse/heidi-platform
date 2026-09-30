// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.heidiverse.heidi.entity.model.credentialscheme.OcaVersion;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "t_style")
public class CredentialSchemeStyleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_style_id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "fk_credential_scheme_id", nullable = false)
    private CredentialSchemeEntity credentialSchemeEntity;

    @NotNull
    @Column(name = "style", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String style;

    @Column(name = "oca_bundle")
    @JdbcTypeCode(SqlTypes.JSON)
    private String ocaBundle;

    @Column(name = "oca_bundle_file_name")
    private String ocaBundleFileName;

    @Column(name = "swiyu_oca_bundle", columnDefinition = "text")
    private String swiyuOcaBundle;

    @Column(name = "swiyu_oca_file_name")
    private String swiyuOcaFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "oca_version", nullable = false)
    private OcaVersion ocaVersion = OcaVersion.LEGACY;

    @Column(name = "typst_template")
    private String typstTemplate;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public CredentialSchemeEntity getCredentialSchemeEntity() {
        return credentialSchemeEntity;
    }

    public void setCredentialSchemeEntity(CredentialSchemeEntity credentialSchemeEntity) {
        this.credentialSchemeEntity = credentialSchemeEntity;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getOcaBundle() {
        return ocaBundle;
    }

    public void setOcaBundle(String ocaBundle) {
        this.ocaBundle = ocaBundle;
    }

    public String getOcaBundleFileName() {
        return ocaBundleFileName;
    }

    public void setOcaBundleFileName(String ocaBundleFileName) {
        this.ocaBundleFileName = ocaBundleFileName;
    }

    public String getSwiyuOcaBundle() {
        return swiyuOcaBundle;
    }

    public void setSwiyuOcaBundle(String swiyuOcaBundle) {
        this.swiyuOcaBundle = swiyuOcaBundle;
    }

    public String getSwiyuOcaFileName() {
        return swiyuOcaFileName;
    }

    public void setSwiyuOcaFileName(String swiyuOcaFileName) {
        this.swiyuOcaFileName = swiyuOcaFileName;
    }

    public OcaVersion getOcaVersion() {
        return ocaVersion == null ? OcaVersion.LEGACY : ocaVersion;
    }

    public void setOcaVersion(OcaVersion ocaVersion) {
        this.ocaVersion = ocaVersion == null ? OcaVersion.LEGACY : ocaVersion;
    }

	public String getTypstTemplate() {
		return typstTemplate;
	}

	public void setTypstTemplate(String typstTemplate) {
		this.typstTemplate = typstTemplate;
	}

}
