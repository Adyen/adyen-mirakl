package com.adyen.mirakl.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import com.adyen.mirakl.domain.enumeration.EmailState;

/**
 * A ProcessEmail.
 */
@Entity
@Table(name = "process_email")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ProcessEmail implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "_to")
    private String to;

    @Column(name = "subject")
    private String subject;

    @Lob
    @Column(name = "content")
    private String content;

    @Column(name = "multipart")
    private Boolean multipart;

    @Column(name = "html")
    private Boolean html;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private EmailState state;

    @OneToMany(mappedBy = "processEmail")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<EmailError> emailErrors = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTo() {
        return to;
    }

    public ProcessEmail to(String to) {
        this.to = to;
        return this;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public ProcessEmail subject(String subject) {
        this.subject = subject;
        return this;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public ProcessEmail content(String content) {
        this.content = content;
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean isMultipart() {
        return multipart;
    }

    public ProcessEmail isMultipart(Boolean multipart) {
        this.multipart = multipart;
        return this;
    }

    public void setIsMultipart(Boolean multipart) {
        this.multipart = multipart;
    }

    public Boolean isHtml() {
        return html;
    }

    public ProcessEmail isHtml(Boolean html) {
        this.html = html;
        return this;
    }

    public void setIsHtml(Boolean html) {
        this.html = html;
    }

    public EmailState getState() {
        return state;
    }

    public ProcessEmail state(EmailState state) {
        this.state = state;
        return this;
    }

    public void setState(EmailState state) {
        this.state = state;
    }

    public Set<EmailError> getEmailErrors() {
        return emailErrors;
    }

    public ProcessEmail emailErrors(Set<EmailError> emailErrors) {
        this.emailErrors = emailErrors;
        return this;
    }

    public ProcessEmail addEmailErrors(EmailError emailErrors) {
        this.emailErrors.add(emailErrors);
        emailErrors.setProcessEmail(this);
        return this;
    }

    public ProcessEmail removeEmailErrors(EmailError emailErrors) {
        this.emailErrors.remove(emailErrors);
        emailErrors.setProcessEmail(null);
        return this;
    }

    public void setEmailErrors(Set<EmailError> emailErrors) {
        this.emailErrors = emailErrors;
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
        ProcessEmail processEmail = (ProcessEmail) o;
        if (processEmail.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), processEmail.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ProcessEmail{" +
            "id=" + getId() +
            ", to='" + getTo() + "'" +
            ", subject='" + getSubject() + "'" +
            ", content='" + getContent() + "'" +
            ", multipart='" + isMultipart() + "'" +
            ", html='" + isHtml() + "'" +
            ", state='" + getState() + "'" +
            "}";
    }
}
