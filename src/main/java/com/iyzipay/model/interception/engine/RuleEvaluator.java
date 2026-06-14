package com.iyzipay.model.interception.engine;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.RuleHitResult;
import com.iyzipay.model.interception.enums.RuleAction;
import com.iyzipay.model.interception.enums.RuleType;
import com.iyzipay.model.interception.rules.BaseRule;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RuleEvaluator implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RuleManager ruleManager;

    private final Map<String, CachedResult> resultCache = new ConcurrentHashMap<>();
    private volatile long cacheTtlMs = 1000;
    private volatile boolean cacheEnabled = true;

    private final AtomicLong totalEvaluationCount = new AtomicLong(0);
    private final AtomicLong totalEvaluationTimeNs = new AtomicLong(0);
    private final AtomicLong blockedCount = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong fastPathCount = new AtomicLong(0);
    private final AtomicLong shortCircuitCount = new AtomicLong(0);

    private static class CachedResult implements Serializable {
        private static final long serialVersionUID = 1L;
        final RuleHitResult result;
        final long timestamp;

        CachedResult(RuleHitResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    public RuleEvaluator(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    public RuleHitResult evaluate(RuleContext context) {
        long startTime = System.nanoTime();
        totalEvaluationCount.incrementAndGet();

        String cacheKey = buildCacheKey(context, "full");
        if (cacheEnabled) {
            CachedResult cached = resultCache.get(cacheKey);
            if (cached != null && !cached.isExpired(cacheTtlMs)) {
                cacheHitCount.incrementAndGet();
                return copyResult(cached.result);
            }
        }

        RuleHitResult result = new RuleHitResult();
        result.setRuleSetVersion(ruleManager.getRuleSetVersion());

        List<BaseRule> activeRules = ruleManager.getAllActiveRules();
        result.setTotalRulesEvaluated(activeRules.size());

        for (BaseRule rule : activeRules) {
            RuleHitResult ruleResult = rule.evaluate(context);
            if (ruleResult.getMatchedRulesCount() > 0) {
                result.merge(ruleResult);
                if (shouldShortCircuit(rule, result)) {
                    shortCircuitCount.incrementAndGet();
                    break;
                }
            }
        }

        result.markEvaluationComplete();
        recordEvaluationTime(startTime);

        if (result.isBlocked()) {
            blockedCount.incrementAndGet();
        }

        if (cacheEnabled) {
            resultCache.put(cacheKey, new CachedResult(copyResult(result), System.currentTimeMillis()));
        }

        return result;
    }

    public RuleHitResult fastEvaluate(RuleContext context) {
        long startTime = System.nanoTime();
        totalEvaluationCount.incrementAndGet();
        fastPathCount.incrementAndGet();

        String cacheKey = buildCacheKey(context, "fast");
        if (cacheEnabled) {
            CachedResult cached = resultCache.get(cacheKey);
            if (cached != null && !cached.isExpired(cacheTtlMs)) {
                cacheHitCount.incrementAndGet();
                return copyResult(cached.result);
            }
        }

        RuleHitResult result = new RuleHitResult();
        result.setRuleSetVersion(ruleManager.getRuleSetVersion());

        List<BaseRule> cardNumberRules = ruleManager.getRulesByType(RuleType.CARD_NUMBER);
        result.setTotalRulesEvaluated(cardNumberRules.size());

        for (BaseRule rule : cardNumberRules) {
            RuleHitResult ruleResult = rule.evaluate(context);
            if (ruleResult.getMatchedRulesCount() > 0) {
                result.merge(ruleResult);
                if (result.isBlocked()) {
                    shortCircuitCount.incrementAndGet();
                    break;
                }
            }
        }

        result.markEvaluationComplete();
        recordEvaluationTime(startTime);

        if (result.isBlocked()) {
            blockedCount.incrementAndGet();
        }

        if (cacheEnabled) {
            resultCache.put(cacheKey, new CachedResult(copyResult(result), System.currentTimeMillis()));
        }

        return result;
    }

    private boolean shouldShortCircuit(BaseRule rule, RuleHitResult result) {
        return result.isBlocked() && rule.getPriority() >= 100;
    }

    private String buildCacheKey(RuleContext context, String path) {
        StringBuilder sb = new StringBuilder(path);
        sb.append("|").append(context.getCardNumber() != null ? context.getCardNumber() : "");
        sb.append("|").append(context.getCardToken() != null ? context.getCardToken() : "");
        sb.append("|").append(context.getCardUserKey() != null ? context.getCardUserKey() : "");
        sb.append("|").append(context.getDeviceId() != null ? context.getDeviceId() : "");
        sb.append("|").append(context.getIpAddress() != null ? context.getIpAddress() : "");
        sb.append("|").append(ruleManager.getRuleSetVersion());
        return sb.toString();
    }

    private RuleHitResult copyResult(RuleHitResult original) {
        RuleHitResult copy = new RuleHitResult();
        copy.setBlocked(original.isBlocked());
        copy.setReviewRequired(original.isReviewRequired());
        copy.setChallengeRequired(original.isChallengeRequired());
        copy.setFinalAction(original.getFinalAction());
        copy.setExplanation(original.getExplanation());
        copy.setRuleSetVersion(original.getRuleSetVersion());
        copy.setEvaluationTimeMs(original.getEvaluationTimeMs());
        copy.setTotalRulesEvaluated(original.getTotalRulesEvaluated());
        copy.setGrayApplied(original.isGrayApplied());
        copy.setGrayRuleId(original.getGrayRuleId());
        for (String ruleId : original.getHitRuleIds()) {
            copy.getHitRuleIds().add(ruleId);
        }
        for (String detail : original.getHitDetails()) {
            copy.getHitDetails().add(detail);
        }
        return copy;
    }

    private void recordEvaluationTime(long startTimeNano) {
        long elapsed = System.nanoTime() - startTimeNano;
        totalEvaluationTimeNs.addAndGet(elapsed);
    }

    public double getAverageEvaluationTimeMs() {
        long count = totalEvaluationCount.get();
        if (count == 0) {
            return 0;
        }
        return (totalEvaluationTimeNs.get() / 1_000_000.0) / count;
    }

    public double getCacheHitRate() {
        long count = totalEvaluationCount.get();
        if (count == 0) {
            return 0;
        }
        return (cacheHitCount.get() * 100.0) / count;
    }

    public double getBlockRate() {
        long count = totalEvaluationCount.get();
        if (count == 0) {
            return 0;
        }
        return (blockedCount.get() * 100.0) / count;
    }

    public String getStatsSummary() {
        return String.format(
                "totalEvaluations=%d, avgTimeMs=%.3f, blocked=%d (%.2f%%), " +
                        "cacheHits=%d (%.2f%%), fastPath=%d, shortCircuits=%d, cacheSize=%d",
                totalEvaluationCount.get(),
                getAverageEvaluationTimeMs(),
                blockedCount.get(),
                getBlockRate(),
                cacheHitCount.get(),
                getCacheHitRate(),
                fastPathCount.get(),
                shortCircuitCount.get(),
                resultCache.size()
        );
    }

    public void resetStats() {
        totalEvaluationCount.set(0);
        totalEvaluationTimeNs.set(0);
        blockedCount.set(0);
        cacheHitCount.set(0);
        fastPathCount.set(0);
        shortCircuitCount.set(0);
        clearCache();
    }

    public void clearCache() {
        resultCache.clear();
    }

    public void cleanupExpiredCache() {
        long now = System.currentTimeMillis();
        resultCache.entrySet().removeIf(entry -> entry.getValue().isExpired(cacheTtlMs));
    }

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheSize() {
        return resultCache.size();
    }

    public long getTotalEvaluationCount() {
        return totalEvaluationCount.get();
    }

    public long getBlockedCount() {
        return blockedCount.get();
    }

    public long getCacheHitCount() {
        return cacheHitCount.get();
    }

    public long getFastPathCount() {
        return fastPathCount.get();
    }

    public long getShortCircuitCount() {
        return shortCircuitCount.get();
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("totalEvaluations", totalEvaluationCount.get())
                .append("avgTimeMs", String.format("%.3f", getAverageEvaluationTimeMs()))
                .append("blocked", blockedCount.get())
                .append("cacheHitRate", String.format("%.2f%%", getCacheHitRate()))
                .append("cacheEnabled", cacheEnabled)
                .append("cacheSize", resultCache.size())
                .toString();
    }
}
