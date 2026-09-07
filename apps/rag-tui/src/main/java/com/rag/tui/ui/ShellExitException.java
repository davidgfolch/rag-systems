package com.rag.tui.ui;

public class ShellExitException extends RuntimeException {
    public ShellExitException() {
        super("exit");
    }
}
