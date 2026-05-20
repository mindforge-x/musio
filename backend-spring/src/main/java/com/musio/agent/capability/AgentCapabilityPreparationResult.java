package com.musio.agent.capability;

import java.util.Map;

public record AgentCapabilityPreparationResult(
        boolean valid,
        Map<String, Object> arguments,
        String reason
) {
    public AgentCapabilityPreparationResult {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        reason = reason == null ? "" : reason.strip();
    }

    public static AgentCapabilityPreparationResult accepted(Map<String, Object> arguments) {
        return new AgentCapabilityPreparationResult(true, arguments, "");
    }

    public static AgentCapabilityPreparationResult rejected(String reason) {
        return new AgentCapabilityPreparationResult(false, Map.of(), reason);
    }
}
