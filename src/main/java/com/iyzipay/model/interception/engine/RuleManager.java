package com.iyzipay.model.interception.engine;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.config.RuleVersion;
import com.iyzipay.model.interception.enums.RuleStatus;
import com.iyzipay.model.interception.enums.RuleType;
import com.iyzipay.model.interception.rules.BaseRule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RuleManager implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, BaseRule> rules = new ConcurrentHashMap<>();
    private final Map<RuleType, List<BaseRule>> rulesByType = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile String ruleSetVersion;
    private final AtomicInteger ruleSetVersionCounter = new AtomicInteger(1);
    private volatile long lastUpdateTime;

    public RuleManager() {
        this.ruleSetVersion = generateRuleSetVersion();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    private String generateRuleSetVersion() {
        return "v" + ruleSetVersionCounter.getAndIncrement() + "-" + System.currentTimeMillis();
    }

    private void invalidateRuleSetVersion() {
        this.ruleSetVersion = generateRuleSetVersion();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    private void rebuildTypeIndex() {
        rulesByType.clear();
        for (BaseRule rule : rules.values()) {
            rulesByType.computeIfAbsent(rule.getRuleType(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(rule);
        }
        for (Map.Entry<RuleType, List<BaseRule>> entry : rulesByType.entrySet()) {
            List<BaseRule> sorted = new ArrayList<>(entry.getValue());
            sorted.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
            entry.setValue(sorted);
        }
    }

    public void addRule(BaseRule rule) {
        if (rule == null || rule.getRuleId() == null) {
            throw new IllegalArgumentException("Rule and ruleId must not be null");
        }
        lock.writeLock().lock();
        try {
            if (rules.containsKey(rule.getRuleId())) {
                throw new IllegalArgumentException("Rule with id " + rule.getRuleId() + " already exists");
            }
            rules.put(rule.getRuleId(), rule);
            rebuildTypeIndex();
            invalidateRuleSetVersion();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateRule(BaseRule rule) {
        if (rule == null || rule.getRuleId() == null) {
            throw new IllegalArgumentException("Rule and ruleId must not be null");
        }
        lock.writeLock().lock();
        try {
            if (!rules.containsKey(rule.getRuleId())) {
                throw new IllegalArgumentException("Rule with id " + rule.getRuleId() + " does not exist");
            }
            rules.put(rule.getRuleId(), rule);
            rebuildTypeIndex();
            invalidateRuleSetVersion();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteRule(String ruleId) {
        if (ruleId == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            rules.remove(ruleId);
            rebuildTypeIndex();
            invalidateRuleSetVersion();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public BaseRule getRule(String ruleId) {
        lock.readLock().lock();
        try {
            return rules.get(ruleId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<BaseRule> getRulesByType(RuleType type) {
        lock.readLock().lock();
        try {
            List<BaseRule> typeRules = rulesByType.get(type);
            if (typeRules == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(typeRules);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<BaseRule> getAllRules() {
        lock.readLock().lock();
        try {
            List<BaseRule> allRules = new ArrayList<>(rules.values());
            allRules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
            return allRules;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<BaseRule> getAllActiveRules() {
        lock.readLock().lock();
        try {
            List<BaseRule> activeRules = new ArrayList<>();
            for (BaseRule rule : rules.values()) {
                if (rule.getStatus() == RuleStatus.ACTIVE || rule.getStatus() == RuleStatus.GRAY_RELEASE) {
                    activeRules.add(rule);
                }
            }
            activeRules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
            return activeRules;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<BaseRule> getAllInactiveRules() {
        lock.readLock().lock();
        try {
            List<BaseRule> inactiveRules = new ArrayList<>();
            for (BaseRule rule : rules.values()) {
                if (rule.getStatus() != RuleStatus.ACTIVE && rule.getStatus() != RuleStatus.GRAY_RELEASE) {
                    inactiveRules.add(rule);
                }
            }
            return inactiveRules;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getActiveRuleCount() {
        return (int) rules.values().stream()
                .filter(r -> r.getStatus() == RuleStatus.ACTIVE || r.getStatus() == RuleStatus.GRAY_RELEASE)
                .count();
    }

    public int getTotalRuleCount() {
        return rules.size();
    }

    public BaseRule rollbackRule(String ruleId, int toVersion, String operator) {
        lock.writeLock().lock();
        try {
            BaseRule rule = rules.get(ruleId);
            if (rule == null) {
                return null;
            }
            boolean success = rule.rollbackToVersion(toVersion, operator);
            if (success) {
                invalidateRuleSetVersion();
            }
            return success ? rule : null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void activateRule(String ruleId, String operator) {
        lock.writeLock().lock();
        try {
            BaseRule rule = rules.get(ruleId);
            if (rule != null) {
                rule.activate();
                rule.setUpdatedBy(operator);
                invalidateRuleSetVersion();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deactivateRule(String ruleId, String operator) {
        lock.writeLock().lock();
        try {
            BaseRule rule = rules.get(ruleId);
            if (rule != null) {
                rule.deactivate();
                rule.setUpdatedBy(operator);
                invalidateRuleSetVersion();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void startGrayRelease(String ruleId, int trafficPercentage, String operator) {
        lock.writeLock().lock();
        try {
            BaseRule rule = rules.get(ruleId);
            if (rule != null) {
                rule.startGrayRelease(trafficPercentage, operator);
                invalidateRuleSetVersion();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void promoteFromGray(String ruleId, String operator) {
        lock.writeLock().lock();
        try {
            BaseRule rule = rules.get(ruleId);
            if (rule != null) {
                rule.promoteFromGray(operator);
                invalidateRuleSetVersion();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<RuleVersion> getRuleVersionHistory(String ruleId) {
        BaseRule rule = rules.get(ruleId);
        if (rule == null) {
            return Collections.emptyList();
        }
        return rule.getVersionHistory();
    }

    public String getRuleSetVersion() {
        return ruleSetVersion;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void resetAllStats() {
        for (BaseRule rule : rules.values()) {
            rule.resetStats();
        }
    }

    public Map<String, Object> getStatsSummary() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        int totalRules = rules.size();
        int activeRules = getActiveRuleCount();
        long totalHits = 0;
        long totalEvaluations = 0;
        double totalAvgTime = 0;

        for (BaseRule rule : rules.values()) {
            totalHits += rule.getHitCount();
            totalEvaluations += rule.getEvaluationCount();
            totalAvgTime += rule.getAverageEvaluationTimeMs();
        }

        stats.put("totalRules", totalRules);
        stats.put("activeRules", activeRules);
        stats.put("totalHits", totalHits);
        stats.put("totalEvaluations", totalEvaluations);
        stats.put("avgEvaluationTimeMs", totalRules > 0 ? totalAvgTime / totalRules : 0);
        stats.put("overallHitRate", totalEvaluations > 0 ? (totalHits * 100.0 / totalEvaluations) : 0);
        stats.put("ruleSetVersion", ruleSetVersion);
        stats.put("lastUpdateTime", lastUpdateTime);

        return stats;
    }

    public void clearAllRules() {
        lock.writeLock().lock();
        try {
            rules.clear();
            rulesByType.clear();
            invalidateRuleSetVersion();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("totalRules", rules.size())
                .append("activeRules", getActiveRuleCount())
                .append("ruleSetVersion", ruleSetVersion)
                .append("lastUpdateTime", lastUpdateTime)
                .toString();
    }
}
