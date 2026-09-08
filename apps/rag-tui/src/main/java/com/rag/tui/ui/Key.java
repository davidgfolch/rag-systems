package com.rag.tui.ui;

/** A decoded input event for the filterable picker. */
record Key(KeyType type, char value) {

    enum KeyType { ESC, ENTER, UP, DOWN, BACKSPACE, TYPE, NONE }
}
