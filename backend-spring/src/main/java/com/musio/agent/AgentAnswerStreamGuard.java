package com.musio.agent;

import java.util.Locale;
import java.util.Optional;

final class AgentAnswerStreamGuard {
    private static final int PREFIX_WAIT_LIMIT = 48;
    private static final String[] RAW_TOOL_MARKERS = {
            "<tool_call",
            "<function=",
            "<tool>",
            "{\"tool",
            "{\"tool_call",
            "{\"function"
    };

    private final StringBuilder visibleAnswer = new StringBuilder();
    private final StringBuilder withheld = new StringBuilder();
    private boolean publishingStarted;
    private boolean rawToolProtocolSuppressed;

    Optional<String> accept(String chunk) {
        if (chunk == null || chunk.isEmpty() || rawToolProtocolSuppressed) {
            return Optional.empty();
        }
        if (!publishingStarted) {
            withheld.append(chunk);
            String candidate = protocolCandidate(withheld.toString());
            if (startsRawToolProtocol(candidate)) {
                rawToolProtocolSuppressed = true;
                withheld.setLength(0);
                visibleAnswer.setLength(0);
                return Optional.empty();
            }
            if (couldStillBecomeRawToolProtocol(candidate)) {
                return Optional.empty();
            }
            publishingStarted = true;
            String text = withheld.toString();
            withheld.setLength(0);
            visibleAnswer.append(text);
            return Optional.of(text);
        }

        visibleAnswer.append(chunk);
        return Optional.of(chunk);
    }

    Optional<String> finish(String fallbackText) {
        if (rawToolProtocolSuppressed) {
            visibleAnswer.setLength(0);
            visibleAnswer.append(fallbackText);
            return Optional.of(fallbackText);
        }
        if (!publishingStarted && !withheld.isEmpty()) {
            publishingStarted = true;
            String text = withheld.toString();
            withheld.setLength(0);
            visibleAnswer.append(text);
            return Optional.of(text);
        }
        return Optional.empty();
    }

    String visibleAnswer() {
        return visibleAnswer.toString();
    }

    boolean rawToolProtocolSuppressed() {
        return rawToolProtocolSuppressed;
    }

    private boolean startsRawToolProtocol(String candidate) {
        if (candidate.isBlank()) {
            return false;
        }
        for (String marker : RAW_TOOL_MARKERS) {
            if (candidate.startsWith(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean couldStillBecomeRawToolProtocol(String candidate) {
        if (candidate.isBlank()) {
            return true;
        }
        if (candidate.length() > PREFIX_WAIT_LIMIT) {
            return false;
        }
        for (String marker : RAW_TOOL_MARKERS) {
            if (marker.startsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String protocolCandidate(String value) {
        String candidate = value.stripLeading().toLowerCase(Locale.ROOT);
        if (candidate.startsWith("```")) {
            int newline = candidate.indexOf('\n');
            if (newline >= 0) {
                return candidate.substring(newline + 1).stripLeading();
            }
        }
        return candidate;
    }
}
