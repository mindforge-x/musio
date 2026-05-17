package com.musio.config;

import com.musio.model.SourceContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RuntimeSourceContextService {
    private final AtomicReference<SourceContext> context;

    public RuntimeSourceContextService(Environment environment) {
        this.context = new AtomicReference<>(fromEnvironment(environment));
    }

    public SourceContext context() {
        return context.get();
    }

    public SourceContext update(SourceContext next) {
        SourceContext normalized = next == null ? SourceContext.defaultContext() : next;
        context.set(normalized);
        return normalized;
    }

    private SourceContext fromEnvironment(Environment environment) {
        String selected = environment.getProperty("musio.sources.selected", SourceContext.DEFAULT_SOURCE);
        String active = environment.getProperty("musio.sources.active", SourceContext.DEFAULT_SOURCE);
        List<String> selectedSources = Arrays.stream(selected.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
        return new SourceContext(selectedSources, active, SourceContext.DEFAULT_USER_ID);
    }
}
