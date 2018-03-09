package com.adyen.mirakl.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A MiraklDocumentDelta.
 */
@Entity
@Table(name = "mirakl_document_delta")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class MiraklDocumentDelta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_delta")
    private ZonedDateTime documentDelta;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getDocumentDelta() {
        return documentDelta;
    }

    public MiraklDocumentDelta documentDelta(ZonedDateTime documentDelta) {
        this.documentDelta = documentDelta;
        return this;
    }

    public void setDocumentDelta(ZonedDateTime documentDelta) {
        this.documentDelta = documentDelta;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MiraklDocumentDelta miraklDocumentDelta = (MiraklDocumentDelta) o;
        if (miraklDocumentDelta.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), miraklDocumentDelta.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MiraklDocumentDelta{" +
            "id=" + getId() +
            ", documentDelta='" + getDocumentDelta() + "'" +
            "}";
    }
}
