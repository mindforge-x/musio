package com.musio.model;

import java.util.Map;

public record PendingConfirmation(
        String actionId,
        boolean approved,
        Map<String, Object> editedInput
) {
}
