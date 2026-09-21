package com.rag.tui.ui;

/** A decoded input event for the filterable picker. */
record Key(KeyType type, char value) {

    enum KeyType {
        ESC, ENTER, UP, DOWN, LEFT, RIGHT, BACKSPACE, TYPE, NONE,
        HOME, END, DELETE, WORD_LEFT, WORD_RIGHT, WORD_BACKSPACE
    }
}
