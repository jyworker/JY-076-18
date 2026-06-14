package com.iyzipay.model.interception;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.Address;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.PaymentCard;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class RuleContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String merchantId;
    private PaymentCard paymentCard;
    private Buyer buyer;
    private Address billingAddress;
    private Address shippingAddress;
    private BigDecimal amount;
    private String currency;
    private String cardBin;
    private String ipAddress;
    private String deviceId;
    private String deviceFingerprint;
    private String userAgent;
    private long timestamp;
    private final Map<String, Object> attributes = new HashMap<>();

    public RuleContext() {
        this.timestamp = System.currentTimeMillis();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public PaymentCard getPaymentCard() {
        return paymentCard;
    }

    public void setPaymentCard(PaymentCard paymentCard) {
        this.paymentCard = paymentCard;
        if (paymentCard != null && paymentCard.getCardNumber() != null
                && paymentCard.getCardNumber().length() >= 6 && this.cardBin == null) {
            this.cardBin = paymentCard.getCardNumber().substring(0, 6);
        }
    }

    public Buyer getBuyer() {
        return buyer;
    }

    public void setBuyer(Buyer buyer) {
        this.buyer = buyer;
    }

    public Address getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCardBin() {
        return cardBin;
    }

    public void setCardBin(String cardBin) {
        this.cardBin = cardBin;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Object getAttribute(String key, Object defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public String getCardNumber() {
        return paymentCard != null ? paymentCard.getCardNumber() : null;
    }

    public String getCardToken() {
        return paymentCard != null ? paymentCard.getCardToken() : null;
    }

    public String getCardUserKey() {
        return paymentCard != null ? paymentCard.getCardUserKey() : null;
    }

    public String getBuyerCountry() {
        if (buyer != null && buyer.getCountry() != null) {
            return buyer.getCountry();
        }
        if (billingAddress != null && billingAddress.getCountry() != null) {
            return billingAddress.getCountry();
        }
        return null;
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("requestId", requestId)
                .append("merchantId", merchantId)
                .append("amount", amount)
                .append("currency", currency)
                .append("cardBin", cardBin)
                .append("ipAddress", ipAddress)
                .append("deviceId", deviceId)
                .append("timestamp", timestamp)
                .toString();
    }
}
