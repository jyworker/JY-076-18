package com.iyzipay.model.interception.config;

import com.iyzipay.ToStringRequestBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RuleVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    private int version;
    private String ruleId;
    private String ruleSnapshot;
    private String operator;
    private String changeDescription;
    private long timestamp;
    private GrayReleaseConfig grayReleaseConfig;
    private boolean active;
    private final List<String> changeLog = new CopyOnWriteArrayList<>();

    public RuleVersion() {
        this.version = 1;
        this.timestamp = System.currentTimeMillis();
        this.active = true;
    }

    public RuleVersion(int version, String ruleId, String operator, String changeDescription) {
        this.version = version;
        this.ruleId = ruleId;
        this.operator = operator;
        this.changeDescription = changeDescription;
        this.timestamp = System.currentTimeMillis();
        this.active = true;
    }

    public static RuleVersion createInitial(String ruleId, String operator) {
        RuleVersion version = new RuleVersion();
        version.setRuleId(ruleId);
        version.setOperator(operator);
        version.setChangeDescription("Initial version");
        version.addChangeLog("Created initial version");
        return version;
    }

    public RuleVersion copyWithNewVersion(int newVersion, String operator, String changeDescription) {
        RuleVersion copy = new RuleVersion();
        copy.setVersion(newVersion);
        copy.setRuleId(this.ruleId);
        copy.setOperator(operator);
        copy.setChangeDescription(changeDescription);
        copy.setRuleSnapshot(this.ruleSnapshot);
        copy.setGrayReleaseConfig(this.grayReleaseConfig);
        copy.setActive(true);
        copy.getChangeLog().addAll(this.changeLog);
        copy.addChangeLog("Updated to version " + newVersion + " by " + operator + ": " + changeDescription);
        return copy;
    }

    public void addChangeLog(String log) {
        changeLog.add(log);
    }

    public List<String> getChangeLog() {
        return new ArrayList<>(changeLog);
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleSnapshot() {
        return ruleSnapshot;
    }

    public void setRuleSnapshot(String ruleSnapshot) {
        this.ruleSnapshot = ruleSnapshot;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public GrayReleaseConfig getGrayReleaseConfig() {
        return grayReleaseConfig;
    }

    public void setGrayReleaseConfig(GrayReleaseConfig grayReleaseConfig) {
        this.grayReleaseConfig = grayReleaseConfig;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("version", version)
                .append("ruleId", ruleId)
                .append("operator", operator)
                .append("changeDescription", changeDescription)
                .append("timestamp", timestamp)
                .append("active", active)
                .append("changeLogSize", changeLog.size())
                .toString();
    }
}
