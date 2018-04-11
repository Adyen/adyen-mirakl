package com.adyen.mirakl.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A MiraklVoucherEntry.
 */
@Entity
@Table(name = "mirakl_voucher_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class MiraklVoucherEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    @CreationTimestamp
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private ZonedDateTime updatedAt;

    @Column(name = "shop_id")
    private String shopId;

    @Column(name = "transfer_amount")
    private String transferAmount;

    @Column(name = "currency_iso_code")
    private String currencyIsoCode;

    @Column(name = "iban")
    private String iban;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "subscription_amount")
    private String subscriptionAmount;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShopId() {
        return shopId;
    }

    public MiraklVoucherEntry shopId(String shopId) {
        this.shopId = shopId;
        return this;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getTransferAmount() {
        return transferAmount;
    }

    public MiraklVoucherEntry transferAmount(String transferAmount) {
        this.transferAmount = transferAmount;
        return this;
    }

    public void setTransferAmount(String transferAmount) {
        this.transferAmount = transferAmount;
    }

    public String getCurrencyIsoCode() {
        return currencyIsoCode;
    }

    public MiraklVoucherEntry currencyIsoCode(String currencyIsoCode) {
        this.currencyIsoCode = currencyIsoCode;
        return this;
    }

    public void setCurrencyIsoCode(String currencyIsoCode) {
        this.currencyIsoCode = currencyIsoCode;
    }

    public String getIban() {
        return iban;
    }

    public MiraklVoucherEntry iban(String iban) {
        this.iban = iban;
        return this;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public MiraklVoucherEntry invoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
        return this;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getShopName() {
        return shopName;
    }

    public MiraklVoucherEntry shopName(String shopName) {
        this.shopName = shopName;
        return this;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public MiraklVoucherEntry createdAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public MiraklVoucherEntry updatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSubscriptionAmount() {
        return subscriptionAmount;
    }

    public MiraklVoucherEntry subscriptionAmount(String subscriptionAmount) {
        this.subscriptionAmount = subscriptionAmount;
        return this;
    }

    public void setSubscriptionAmount(String subscriptionAmount) {
        this.subscriptionAmount = subscriptionAmount;
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
        MiraklVoucherEntry miraklVoucherEntry = (MiraklVoucherEntry) o;
        if (miraklVoucherEntry.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), miraklVoucherEntry.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MiraklVoucherEntry{"
            + "id="
            + getId()
            + ", shopId='"
            + getShopId()
            + "'"
            + ", transferAmount='"
            + getTransferAmount()
            + "'"
            + ", currencyIsoCode='"
            + getCurrencyIsoCode()
            + "'"
            + ", iban='"
            + getIban()
            + "'"
            + ", invoiceNumber='"
            + getInvoiceNumber()
            + "'"
            + ", shopName='"
            + getShopName()
            + "'"
            + ", createdAt='"
            + getCreatedAt()
            + "'"
            + ", updatedAt='"
            + getUpdatedAt()
            + "'"
            + ", subscriptionAmount='"
            + getSubscriptionAmount()
            + "'"
            + "}";
    }

    public boolean hasSubscription() {
        if (StringUtils.isEmpty(subscriptionAmount)) {
            return false;
        }

        BigDecimal decimalAmount = new BigDecimal(subscriptionAmount);
        return BigDecimal.ZERO.compareTo(decimalAmount) != 0;
    }
}
