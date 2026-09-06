package com.rag.tui.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleLifecycleManagerTest {

    private final ModuleLifecycleManager.ProcessStarter starter = mock(ModuleLifecycleManager.ProcessStarter.class);
    private final ModuleLifecycleManager sut = new ModuleLifecycleManager(starter);
    private final Module module = new Module("rag-basic", "http://localhost:8081");

    @Test
    void startsAndTracksModuleProcess() throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(starter.start(expectedScript(), "rag-basic")).thenReturn(process);

        boolean started = sut.start(module);

        assertThat(started).isTrue();
        assertThat(sut.isRunning("rag-basic")).isTrue();
    }

    @Test
    void doesNotStartTwice() throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(starter.start(any(String[].class))).thenReturn(process);
        sut.start(module);

        boolean second = sut.start(module);

        assertThat(second).isFalse();
    }

    @Test
    void stopsRunningModule() {
        runningModule();

        boolean stopped = sut.stop("rag-basic");

        assertThat(stopped).isTrue();
        assertThat(sut.isRunning("rag-basic")).isFalse();
    }

    @Test
    void reportsNotRunningForUnknownModule() {
        assertThat(sut.isRunning("nope")).isFalse();
        assertThat(sut.stop("nope")).isFalse();
    }

@Test
    void throwsStartExceptionWhenLaunchFails() throws Exception {
        when(starter.start(any(String[].class)))
                .thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> sut.start(module))
                .isInstanceOf(ModuleLifecycleManager.StartException.class);
    }

    @Test
    void defaultStarterFailsFastWhenScriptMissing() {
        Path projectDir = Path.of(System.getProperty("java.io.tmpdir"));
        ModuleLifecycleManager raw = new ModuleLifecycleManager(projectDir.toString());

        assertThatThrownBy(() -> raw.start(module))
                .isInstanceOf(ModuleLifecycleManager.StartException.class);
    }

    private String expectedScript() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "scripts\\run.bat" : "scripts/run.sh";
    }

    private void runningModule() {
        try {
            Process process = mock(Process.class);
            when(process.isAlive()).thenReturn(true);
            when(starter.start(any(String[].class))).thenReturn(process);
            sut.start(module);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}