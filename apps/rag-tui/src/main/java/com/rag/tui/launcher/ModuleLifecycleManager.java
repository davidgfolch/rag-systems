package com.rag.tui.launcher;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts/stops rag-* modules as child processes (the "run" script) and tracks
 * their liveness. Kept thin: no RAG logic, only process management.
 */
public class ModuleLifecycleManager {

    private final String projectDir;
    private final ProcessStarter starter;
    private final Map<String, Process> running = new ConcurrentHashMap<>();

    public ModuleLifecycleManager(String projectDir) {
        this(projectDir, command -> new ProcessBuilder(command)
                .directory(new java.io.File(projectDir))
                .redirectErrorStream(true)
                .start());
    }

    ModuleLifecycleManager(String projectDir, ProcessStarter starter) {
        this.projectDir = projectDir;
        this.starter = starter;
    }

    public boolean start(Module module) {
        if (isRunning(module.name())) return false;
        try {
            running.put(module.name(), starter.start(script(), module.name()));
            return true;
        } catch (IOException e) {
            throw new StartException("Failed to start " + module.name(), e);
        }
    }

    public boolean stop(String name) {
        Process process = running.remove(name);
        if (process == null) return false;
        process.destroy();
        return true;
    }

    public boolean isRunning(String name) {
        Process process = running.get(name);
        return process != null && process.isAlive();
    }

    private String script() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "scripts\\run.bat" : "scripts/run.sh";
    }

    @FunctionalInterface
    public interface ProcessStarter {
        Process start(String... command) throws IOException;
    }

    public static class StartException extends RuntimeException {
        public StartException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}