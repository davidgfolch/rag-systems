package com.rag.tui.fetching;

/**
 * Fetched web page containing the source URL and its plain-text content.
 */
public record WebPage(String url, String text) {}
