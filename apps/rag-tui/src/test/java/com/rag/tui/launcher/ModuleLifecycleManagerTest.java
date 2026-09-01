package com.rag.tui.launcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModuleLifecycleManagerTest {

    private final ModuleLifecycleManager.ProcessStarter starter = mock(ModuleLifecycleManager.ProcessStarter.class);
    private final ModuleLifecycleManager sut = new ModuleLifecycleManager("C:/work", starter);
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
        when(starter.start(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(process);
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
        when(starter.start(org.mockito.ArgumentMatchers.any(String[].class)))
                .thenThrow(new java.io.IOException("boom"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.start(module))
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
            when(starter.start(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(process);
            sut.start(module);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}