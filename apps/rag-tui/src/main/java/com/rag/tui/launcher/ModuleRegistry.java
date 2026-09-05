package com.rag.tui.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of known rag-* modules plus the currently active one. Immutable list,
 * mutable active selection (switched at runtime with the "use" command).
 */
public class ModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistry.class);

    private final List<Module> modules;
    private final Map<String, Module> byName;
    private volatile String activeName;

    public ModuleRegistry(List<Module> modules, String defaultActive) {
        this.modules = List.copyOf(modules);
        this.byName = modules.stream().collect(java.util.stream.Collectors.toMap(Module::name, m -> m));
        this.activeName = byName.containsKey(defaultActive) ? defaultActive : modules.get(0).name();
    }

    public List<Module> modules() {
        return modules;
    }

    public Optional<Module> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Module active() {
        return byName.get(activeName);
    }

    public boolean activate(String name) {
        if (!byName.containsKey(name)) return false;
        log.info("Activated module {}", name);
        activeName = name;
        return true;
    }
}