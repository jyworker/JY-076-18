package com.iyzipay.model.interception.rules;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.enums.FrequencyUnit;
import com.iyzipay.model.interception.enums.RuleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FrequencyRule extends BaseRule {

    private static final long serialVersionUID = 1L;

    private int threshold;
    private FrequencyUnit timeUnit;
    private String dimension;
    private boolean monitorOnly;

    private final ConcurrentHashMap<String, FrequencyWindow> frequencyMap = new ConcurrentHashMap<>();
    private final long cleanupIntervalMs = 60_000;
    private volatile long lastCleanupTime;

    public static class FrequencyWindow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String key;
        private final long windowSizeMs;
        private final AtomicLong windowStart;
        private final AtomicInteger count;
        private final List<Long> timestamps;

        public FrequencyWindow(String key, long windowSizeMs) {
            this.key = key;
            this.windowSizeMs = windowSizeMs;
            this.windowStart = new AtomicLong(System.currentTimeMillis());
            this.count = new AtomicInteger(0);
            this.timestamps = Collections.synchronizedList(new ArrayList<>());
        }

        public synchronized int incrementAndGet() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() > windowSizeMs) {
                windowStart.set(now);
                count.set(0);
                timestamps.clear();
            }
            timestamps.add(now);
            return count.incrementAndGet();
        }

        public synchronized int getCurrentCount() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() > windowSizeMs) {
                windowStart.set(now);
                count.set(0);
                timestamps.clear();
            }
            return count.get();
        }

        public String getKey() {
            return key;
        }

        public long getWindowStart() {
            return windowStart.get();
        }

        public long getWindowSizeMs() {
            return windowSizeMs;
        }

        public List<Long> getTimestamps() {
            return new ArrayList<>(timestamps);
        }
    }

    public FrequencyRule() {
        super();
        this.ruleType = RuleType.FREQUENCY;
        this.threshold = 10;
        this.timeUnit = FrequencyUnit.PER_MINUTE;
        this.dimension = "CARD_NUMBER";
        this.monitorOnly = false;
        this.lastCleanupTime = System.currentTimeMillis();
    }

    public FrequencyRule(String ruleId, String ruleName) {
        super(ruleId, ruleName);
        this.ruleType = RuleType.FREQUENCY;
        this.threshold = 10;
        this.timeUnit = FrequencyUnit.PER_MINUTE;
        this.dimension = "CARD_NUMBER";
        this.monitorOnly = false;
        this.lastCleanupTime = System.currentTimeMillis();
    }

    @Override
    public boolean isApplicable(RuleContext context) {
        return getFrequencyKey(context) != null;
    }

    @Override
    public boolean matches(RuleContext context) {
        cleanupExpiredWindows();

        String key = getFrequencyKey(context);
        if (key == null) {
            return false;
        }

        long windowSizeMs = getWindowSizeMs();
        FrequencyWindow window = frequencyMap.computeIfAbsent(key, k -> new FrequencyWindow(k, windowSizeMs));

        int currentCount = window.incrementAndGet();
        return currentCount > threshold;
    }

    @Override
    public String getExplanation(RuleContext context) {
        String key = getFrequencyKey(context);
        FrequencyWindow window = key != null ? frequencyMap.get(key) : null;
        int count = window != null ? window.getCurrentCount() : 0;

        return String.format("Frequency limit exceeded: %d/%d %s for dimension %s (key=%s)",
                count, threshold, timeUnit, dimension, key);
    }

    @Override
    public String getHitDetail(RuleContext context) {
        String key = getFrequencyKey(context);
        FrequencyWindow window = key != null ? frequencyMap.get(key) : null;
        int count = window != null ? window.getCurrentCount() : 0;

        return String.format("Rule=%s, Dimension=%s, Key=%s, Count=%d, Threshold=%d, TimeUnit=%s, MonitorOnly=%s",
                ruleId, dimension, key, count, threshold, timeUnit, monitorOnly);
    }

    private String getFrequencyKey(RuleContext context) {
        switch (dimension.toUpperCase()) {
            case "CARD_NUMBER":
                return context.getCardNumber();
            case "CARD_TOKEN":
                return context.getCardToken();
            case "CARD_USER_KEY":
                return context.getCardUserKey();
            case "DEVICE_ID":
                return context.getDeviceId();
            case "IP_ADDRESS":
                return context.getIpAddress();
            case "BUYER_ID":
                return context.getBuyer() != null ? context.getBuyer().getId() : null;
            case "BIN":
                return context.getCardBin();
            case "CARD_AND_DEVICE":
                String card = context.getCardNumber();
                String device = context.getDeviceId();
                return (card != null && device != null) ? (card + "|" + device) : null;
            default:
                return context.getCardNumber();
        }
    }

    private long getWindowSizeMs() {
        switch (timeUnit) {
            case PER_SECOND:
                return 1_000;
            case PER_MINUTE:
                return 60_000;
            case PER_HOUR:
                return 3_600_000;
            case PER_DAY:
                return 86_400_000;
            case PER_WEEK:
                return 604_800_000L;
            case PER_MONTH:
                return 2_592_000_000L;
            default:
                return 60_000;
        }
    }

    private void cleanupExpiredWindows() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < cleanupIntervalMs) {
            return;
        }
        lastCleanupTime = now;

        long maxWindowSize = getWindowSizeMs() * 2;
        frequencyMap.entrySet().removeIf(entry -> {
            FrequencyWindow window = entry.getValue();
            return (now - window.getWindowStart()) > maxWindowSize;
        });
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public FrequencyUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(FrequencyUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public boolean isMonitorOnly() {
        return monitorOnly;
    }

    public void setMonitorOnly(boolean monitorOnly) {
        this.monitorOnly = monitorOnly;
    }

    public int getCurrentFrequency(String key) {
        FrequencyWindow window = frequencyMap.get(key);
        return window != null ? window.getCurrentCount() : 0;
    }

    public void resetFrequency(String key) {
        frequencyMap.remove(key);
    }

    public void resetAllFrequencies() {
        frequencyMap.clear();
    }

    public int getActiveKeyCount() {
        cleanupExpiredWindows();
        return frequencyMap.size();
    }

    @Override
    public void resetStats() {
        super.resetStats();
        resetAllFrequencies();
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .appendSuper(super.toString())
                .append("threshold", threshold)
                .append("timeUnit", timeUnit)
                .append("dimension", dimension)
                .append("activeKeys", getActiveKeyCount())
                .append("monitorOnly", monitorOnly)
                .toString();
    }
}
