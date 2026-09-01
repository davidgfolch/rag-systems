package com.rag.webcrawler.services.ranking;

import com.rag.common.services.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-driven ranker that asks the model which links answer the question.
 * Falls back to the {@link DeterministicLinkPrioritizer} when no question is
 * given or the model yields nothing usable.
 */
public class LlmLinkPrioritizer implements LinkPrioritizer {

    private static final String PROMPT = """
            Rank which of the following links are most likely to answer: %s
            Answer with only the URLs, one per line, most relevant first.
            Links:
            %s""";

    private static final Pattern URL_LINE = Pattern.compile("\\b(https?://\\S+)");

    private final ChatModel chatModel;
    private final LinkPrioritizer fallback;

    public LlmLinkPrioritizer(ChatModel chatModel, LinkPrioritizer fallback) {
        this.chatModel = chatModel;
        this.fallback = fallback;
    }

    @Override
    public List<String> prioritize(List<String> links, String question) {
        if (question == null || question.isBlank()) return fallback.prioritize(links, question);
        List<String> matches = extractUrls(chatModel.complete(PROMPT.formatted(question, String.join("\n", links))));
        List<String> result = new ArrayList<>();
        for (String url : matches) {
            if (links.contains(url)) result.add(url);
        }
        return result.isEmpty() ? fallback.prioritize(links, question) : result;
    }

    private List<String> extractUrls(String answer) {
        List<String> matches = new ArrayList<>();
        if (answer == null) return matches;
        Matcher matcher = URL_LINE.matcher(answer);
        while (matcher.find()) {
            matches.add(matcher.group(1).replaceAll("[.,;:)]$", ""));
        }
        return matches;
    }
}