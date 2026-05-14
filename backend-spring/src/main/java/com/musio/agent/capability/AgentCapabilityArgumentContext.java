package com.musio.agent.capability;

public record AgentCapabilityArgumentContext(
        int requestedSongCount,
        int songIndexMax,
        boolean requireLocalWriteTarget,
        boolean singleTargetLookup
) {
    public AgentCapabilityArgumentContext(
            int requestedSongCount,
            int songIndexMax,
            boolean requireLocalWriteTarget
    ) {
        this(requestedSongCount, songIndexMax, requireLocalWriteTarget, false);
    }

    public AgentCapabilityArgumentContext {
        requestedSongCount = Math.max(0, requestedSongCount);
        songIndexMax = songIndexMax <= 0 ? 100 : songIndexMax;
    }

    public static AgentCapabilityArgumentContext turnPlanner() {
        return new AgentCapabilityArgumentContext(0, 20, false);
    }

    public static AgentCapabilityArgumentContext stepPlanner(int requestedSongCount) {
        return new AgentCapabilityArgumentContext(requestedSongCount, 100, true);
    }

    public static AgentCapabilityArgumentContext stepPlanner(int requestedSongCount, boolean singleTargetLookup) {
        return new AgentCapabilityArgumentContext(requestedSongCount, 100, true, singleTargetLookup);
    }

    public static AgentCapabilityArgumentContext defaultContext() {
        return stepPlanner(0);
    }
}
