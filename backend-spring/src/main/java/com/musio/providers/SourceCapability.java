package com.musio.providers;

import com.musio.agent.capability.AgentCapability;
import com.musio.agent.capability.CapabilityEffect;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SourceCapability(
        String name,
        String tier,
        CapabilityEffect effect,
        String description,
        Map<String, Object> inputSchema,
        Set<String> required,
        boolean requiredCapability,
        boolean enabled,
        String disabledReason,
        String resultType
) {
    public SourceCapability(
            String name,
            CapabilityEffect effect,
            String description,
            Map<String, Object> inputSchema,
            Set<String> required,
            boolean enabled,
            String disabledReason,
            String resultType
    ) {
        this(name, "", effect, description, inputSchema, required, false, enabled, disabledReason, resultType);
    }

    public SourceCapability {
        name = name == null ? "" : name.strip();
        tier = tier == null ? "" : tier.strip().toUpperCase(java.util.Locale.ROOT);
        effect = effect == null ? CapabilityEffect.READ : effect;
        description = description == null ? "" : description.strip();
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        required = required == null ? Set.of() : Set.copyOf(required);
        disabledReason = disabledReason == null ? "" : disabledReason.strip();
        resultType = resultType == null || resultType.isBlank() ? "generic" : resultType.strip();
    }

    public AgentCapability toAgentCapability(String argumentSpec) {
        return new AgentCapability(name, effect, description, argumentSpec, required);
    }

    public List<String> requiredList() {
        return required.stream().toList();
    }

    public boolean isP0() {
        return "P0".equals(tier);
    }
}
