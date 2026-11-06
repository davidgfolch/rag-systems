package com.rag.tui.ui;

import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;

import java.util.List;
import java.util.stream.Collectors;

import static com.rag.tui.ui.TerminalStyle.info;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Resolves the active module once at startup: the only running module is
 * selected automatically; when several run and the active one is down the
 * user picks one; when nothing runs (or the choice is cancelled) the terminal
 * explains how to run or activate a module.
 */
public class ModuleBootstrap {

    /** Companion service for the model catalog; never a RAG chat module. */
    private static final String PROVIDER_MODULE = "rag-provider";
    private static final String CHOOSE_ACTIVE = "Choose the active module";
    private static final String ACTIVE_MODULE = "Active module: ";

    private final ModuleRegistry registry;
    private final ModuleLifecycleManager lifecycle;
    private final ModuleHealthClient healthClient;
    private final Prompter prompter;

    public ModuleBootstrap(ModuleRegistry registry, ModuleLifecycleManager lifecycle,
                           ModuleHealthClient healthClient, Prompter prompter) {
        this.registry = registry;
        this.lifecycle = lifecycle;
        this.healthClient = healthClient;
        this.prompter = prompter;
    }

    /**
     * Modules that can be the active chat module. rag-provider is the model-provider
     * companion, not a RAG pipeline, so 'modules' lists it but selection never offers it.
     */
    static List<Module> selectable(ModuleRegistry registry) {
        return registry.modules().stream()
                .filter(m -> !PROVIDER_MODULE.equals(m.name()))
                .toList();
    }

    public String resolve() {
        var running = selectable(registry).stream().filter(this::isRunning).toList();
        var active = registry.active();
        if (running.isEmpty()) {
            return info("No module running. Active module " + active.name() + " is not running.\n"
                    + "Start one with 'start <module>', or switch with 'use <module>'.");
        }
        if (running.size() == 1) {
            var only = running.get(0);
            registry.activate(only.name());
            return success(ACTIVE_MODULE + only.name() + " (only running module)");
        }
        if (running.stream().anyMatch(m -> m.name().equals(active.name()))) {
            return success(ACTIVE_MODULE + active.name());
        }
        var choices = running.stream().map(m -> new Prompter.Choice(m.name(), m.name())).toList();
        String picked = prompter.pick(CHOOSE_ACTIVE, choices).orElse("");
        if (picked.isEmpty()) {
            return noModuleSelected(running, active.name());
        }
        registry.activate(picked);
        return success(ACTIVE_MODULE + picked);
    }

    private static String noModuleSelected(List<Module> running, String activeName) {
        String names = running.stream().map(Module::name).collect(Collectors.joining(", "));
        return info("Several modules running: " + names + ". Active module " + activeName
                + " is not running; use 'use <module>' to choose one.");
    }

    /** A module counts as running when it is a child process or answers its health probe. */
    private boolean isRunning(Module module) {
        return lifecycle.isRunning(module.name()) || healthClient.isUp(module.baseUrl());
    }
}
