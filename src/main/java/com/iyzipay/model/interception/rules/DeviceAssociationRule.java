package com.iyzipay.model.interception.rules;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.enums.RuleType;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceAssociationRule extends BaseRule {

    private static final long serialVersionUID = 1L;

    private final ConcurrentHashMap<String, Set<String>> cardToDevices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> deviceToCards = new ConcurrentHashMap<>();
    private final Set<String> blockedDeviceIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> blockedDeviceFingerprints = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private int maxCardsPerDevice = 5;
    private int maxDevicesPerCard = 3;
    private boolean checkNewDeviceAssociation = true;
    private boolean blockSharedDevices = true;

    public static class DeviceCardAssociation implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String cardKey;
        private final String deviceId;
        private final String deviceFingerprint;
        private final long firstSeen;
        private final long lastSeen;

        public DeviceCardAssociation(String cardKey, String deviceId, String deviceFingerprint) {
            this.cardKey = cardKey;
            this.deviceId = deviceId;
            this.deviceFingerprint = deviceFingerprint;
            this.firstSeen = System.currentTimeMillis();
            this.lastSeen = System.currentTimeMillis();
        }

        public String getCardKey() {
            return cardKey;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getDeviceFingerprint() {
            return deviceFingerprint;
        }

        public long getFirstSeen() {
            return firstSeen;
        }

        public long getLastSeen() {
            return lastSeen;
        }
    }

    public DeviceAssociationRule() {
        super();
        this.ruleType = RuleType.DEVICE_ASSOCIATION;
    }

    public DeviceAssociationRule(String ruleId, String ruleName) {
        super(ruleId, ruleName);
        this.ruleType = RuleType.DEVICE_ASSOCIATION;
    }

    @Override
    public boolean isApplicable(RuleContext context) {
        return (context.getDeviceId() != null || context.getDeviceFingerprint() != null)
                && (context.getCardNumber() != null || context.getCardToken() != null);
    }

    @Override
    public boolean matches(RuleContext context) {
        String deviceId = context.getDeviceId();
        String deviceFingerprint = context.getDeviceFingerprint();
        String cardKey = getCardKey(context);

        if (deviceId != null && blockedDeviceIds.contains(deviceId)) {
            return true;
        }

        if (deviceFingerprint != null && blockedDeviceFingerprints.contains(deviceFingerprint)) {
            return true;
        }

        if (cardKey == null) {
            return false;
        }

        if (checkNewDeviceAssociation) {
            recordAssociation(cardKey, deviceId, deviceFingerprint);
            if (blockSharedDevices) {
                if (exceedsDeviceCardLimit(deviceId, cardKey)) {
                    return true;
                }
                if (exceedsCardDeviceLimit(cardKey, deviceId)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getExplanation(RuleContext context) {
        String deviceId = context.getDeviceId();
        String deviceFingerprint = context.getDeviceFingerprint();
        String cardKey = getCardKey(context);

        if (deviceId != null && blockedDeviceIds.contains(deviceId)) {
            return "Device ID is blocked: " + deviceId;
        }

        if (deviceFingerprint != null && blockedDeviceFingerprints.contains(deviceFingerprint)) {
            return "Device fingerprint is blocked: " + maskFingerprint(deviceFingerprint);
        }

        if (cardKey != null && deviceId != null) {
            int cardCount = getCardCountForDevice(deviceId);
            int deviceCount = getDeviceCountForCard(cardKey);

            if (cardCount > maxCardsPerDevice) {
                return String.format("Device %s has too many cards: %d/%d",
                        maskDeviceId(deviceId), cardCount, maxCardsPerDevice);
            }
            if (deviceCount > maxDevicesPerCard) {
                return String.format("Card %s used on too many devices: %d/%d",
                        maskCardKey(cardKey), deviceCount, maxDevicesPerCard);
            }
        }

        return "Device association check passed";
    }

    @Override
    public String getHitDetail(RuleContext context) {
        String deviceId = context.getDeviceId();
        String cardKey = getCardKey(context);
        int cardCount = deviceId != null ? getCardCountForDevice(deviceId) : 0;
        int deviceCount = cardKey != null ? getDeviceCountForCard(cardKey) : 0;

        return String.format("Rule=%s, Device=%s, Card=%s, CardsPerDevice=%d/%d, DevicesPerCard=%d/%d",
                ruleId,
                maskDeviceId(deviceId),
                maskCardKey(cardKey),
                cardCount, maxCardsPerDevice,
                deviceCount, maxDevicesPerCard);
    }

    private String getCardKey(RuleContext context) {
        if (context.getCardNumber() != null) {
            return context.getCardNumber();
        }
        if (context.getCardToken() != null) {
            return context.getCardToken();
        }
        if (context.getCardUserKey() != null) {
            return context.getCardUserKey();
        }
        return null;
    }

    private synchronized boolean recordAssociation(String cardKey, String deviceId, String deviceFingerprint) {
        boolean isNew = false;

        if (deviceId != null) {
            Set<String> devices = cardToDevices.computeIfAbsent(cardKey,
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            isNew |= devices.add(deviceId);

            Set<String> cards = deviceToCards.computeIfAbsent(deviceId,
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            cards.add(cardKey);
        }

        return isNew;
    }

    private boolean exceedsDeviceCardLimit(String deviceId, String currentCardKey) {
        if (deviceId == null) {
            return false;
        }
        Set<String> cards = deviceToCards.get(deviceId);
        return cards != null && cards.size() > maxCardsPerDevice;
    }

    private boolean exceedsCardDeviceLimit(String cardKey, String currentDeviceId) {
        if (cardKey == null) {
            return false;
        }
        Set<String> devices = cardToDevices.get(cardKey);
        return devices != null && devices.size() > maxDevicesPerCard;
    }

    public int getCardCountForDevice(String deviceId) {
        if (deviceId == null) {
            return 0;
        }
        Set<String> cards = deviceToCards.get(deviceId);
        return cards != null ? cards.size() : 0;
    }

    public int getDeviceCountForCard(String cardKey) {
        if (cardKey == null) {
            return 0;
        }
        Set<String> devices = cardToDevices.get(cardKey);
        return devices != null ? devices.size() : 0;
    }

    public void blockDeviceId(String deviceId) {
        if (deviceId != null) {
            blockedDeviceIds.add(deviceId);
        }
    }

    public void unblockDeviceId(String deviceId) {
        if (deviceId != null) {
            blockedDeviceIds.remove(deviceId);
        }
    }

    public void blockDeviceFingerprint(String fingerprint) {
        if (fingerprint != null) {
            blockedDeviceFingerprints.add(fingerprint);
        }
    }

    public void unblockDeviceFingerprint(String fingerprint) {
        if (fingerprint != null) {
            blockedDeviceFingerprints.remove(fingerprint);
        }
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return deviceId;
        }
        return deviceId.substring(0, 4) + "****" + deviceId.substring(deviceId.length() - 4);
    }

    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return fingerprint;
        }
        return fingerprint.substring(0, 4) + "****" + fingerprint.substring(fingerprint.length() - 4);
    }

    private String maskCardKey(String cardKey) {
        if (cardKey == null || cardKey.length() < 8) {
            return cardKey;
        }
        return cardKey.substring(0, 4) + "****" + cardKey.substring(cardKey.length() - 4);
    }

    public int getMaxCardsPerDevice() {
        return maxCardsPerDevice;
    }

    public void setMaxCardsPerDevice(int maxCardsPerDevice) {
        this.maxCardsPerDevice = maxCardsPerDevice;
    }

    public int getMaxDevicesPerCard() {
        return maxDevicesPerCard;
    }

    public void setMaxDevicesPerCard(int maxDevicesPerCard) {
        this.maxDevicesPerCard = maxDevicesPerCard;
    }

    public boolean isCheckNewDeviceAssociation() {
        return checkNewDeviceAssociation;
    }

    public void setCheckNewDeviceAssociation(boolean checkNewDeviceAssociation) {
        this.checkNewDeviceAssociation = checkNewDeviceAssociation;
    }

    public boolean isBlockSharedDevices() {
        return blockSharedDevices;
    }

    public void setBlockSharedDevices(boolean blockSharedDevices) {
        this.blockSharedDevices = blockSharedDevices;
    }

    public int getBlockedDeviceCount() {
        return blockedDeviceIds.size() + blockedDeviceFingerprints.size();
    }

    public int getTrackedCardCount() {
        return cardToDevices.size();
    }

    public int getTrackedDeviceCount() {
        return deviceToCards.size();
    }

    public void clearAssociations() {
        cardToDevices.clear();
        deviceToCards.clear();
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .appendSuper(super.toString())
                .append("maxCardsPerDevice", maxCardsPerDevice)
                .append("maxDevicesPerCard", maxDevicesPerCard)
                .append("blockedDevices", getBlockedDeviceCount())
                .append("trackedCards", getTrackedCardCount())
                .append("trackedDevices", getTrackedDeviceCount())
                .toString();
    }
}
