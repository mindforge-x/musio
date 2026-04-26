package com.musio.agent;

import com.musio.model.PendingConfirmation;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfirmationService {
    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    public void save(String runId, PendingConfirmation confirmation) {
        pendingConfirmations.put(runId, confirmation);
    }

    public Optional<PendingConfirmation> find(String runId) {
        return Optional.ofNullable(pendingConfirmations.get(runId));
    }

    public void clear(String runId) {
        pendingConfirmations.remove(runId);
    }
}
