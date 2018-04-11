package com.adyen.mirakl.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A DocError.
 */
@Entity
@Table(name = "doc_errors")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DocError implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error")
    private String error;

    @ManyToOne
    private DocRetry docRetry;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getError() {
        return error;
    }

    public DocError error(String error) {
        this.error = error;
        return this;
    }

    public void setError(String error) {
        this.error = error;
    }

    public DocRetry getDocRetry() {
        return docRetry;
    }

    public DocError docRetry(DocRetry docRetry) {
        this.docRetry = docRetry;
        return this;
    }

    public void setDocRetry(DocRetry docRetry) {
        this.docRetry = docRetry;
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
        DocError docError = (DocError) o;
        if (docError.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), docError.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DocError{" +
            "id=" + getId() +
            ", error='" + getError() + "'" +
            "}";
    }
}
