package com.iyzipay.model.interception.config;

import com.iyzipay.ToStringRequestBuilder;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class GrayReleaseConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;
    private int trafficPercentage;
    private String grayKey;
    private String grayHashAlgorithm;
    private long startTime;
    private long endTime;
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger grayRequests = new AtomicInteger(0);

    public GrayReleaseConfig() {
        this.enabled = false;
        this.trafficPercentage = 0;
        this.grayHashAlgorithm = "MD5";
    }

    public GrayReleaseConfig(int trafficPercentage) {
        this.enabled = trafficPercentage > 0;
        this.trafficPercentage = trafficPercentage;
        this.grayHashAlgorithm = "MD5";
        this.startTime = System.currentTimeMillis();
    }

    public boolean shouldApplyGray(String requestId) {
        if (!enabled || trafficPercentage <= 0) {
            return false;
        }
        if (trafficPercentage >= 100) {
            return true;
        }
        totalRequests.incrementAndGet();
        int hash = Math.abs(requestId != null ? requestId.hashCode() : (int) System.currentTimeMillis());
        boolean result = (hash % 100) < trafficPercentage;
        if (result) {
            grayRequests.incrementAndGet();
        }
        return result;
    }

    public int getGrayHitRatio() {
        int total = totalRequests.get();
        if (total == 0) {
            return 0;
        }
        return (int) ((grayRequests.get() * 100) / total);
    }

    public void resetStats() {
        totalRequests.set(0);
        grayRequests.set(0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTrafficPercentage() {
        return trafficPercentage;
    }

    public void setTrafficPercentage(int trafficPercentage) {
        this.trafficPercentage = trafficPercentage;
        this.enabled = trafficPercentage > 0;
    }

    public String getGrayKey() {
        return grayKey;
    }

    public void setGrayKey(String grayKey) {
        this.grayKey = grayKey;
    }

    public String getGrayHashAlgorithm() {
        return grayHashAlgorithm;
    }

    public void setGrayHashAlgorithm(String grayHashAlgorithm) {
        this.grayHashAlgorithm = grayHashAlgorithm;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getTotalRequests() {
        return totalRequests.get();
    }

    public int getGrayRequests() {
        return grayRequests.get();
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("enabled", enabled)
                .append("trafficPercentage", trafficPercentage)
                .append("grayKey", grayKey)
                .append("startTime", startTime)
                .append("totalRequests", totalRequests.get())
                .append("grayRequests", grayRequests.get())
                .toString();
    }
}
