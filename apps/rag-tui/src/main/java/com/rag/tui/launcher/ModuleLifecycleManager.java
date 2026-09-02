package com.rag.tui.launcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts/stops rag-* modules as child processes (the "run" script) and tracks
 * their liveness. Kept thin: no RAG logic, only process management.
 */
public class ModuleLifecycleManager {

    private final Path projectDir;
    private final ProcessStarter starter;
    private final Map<String, Process> running = new ConcurrentHashMap<>();

    public ModuleLifecycleManager(String projectDir) {
        this(Path.of(projectDir), command -> new ProcessBuilder(command)
                .directory(new java.io.File(projectDir))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start());
    }

    ModuleLifecycleManager(ProcessStarter starter) {
        this(null, starter);
    }

    private ModuleLifecycleManager(Path projectDir, ProcessStarter starter) {
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
        String name = System.getProperty("os.name", "").toLowerCase().contains("win") ? "run.bat" : "run.sh";
        Path base = projectDir != null ? projectDir : Path.of("");
        return base.resolve("scripts").resolve(name).toString();
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