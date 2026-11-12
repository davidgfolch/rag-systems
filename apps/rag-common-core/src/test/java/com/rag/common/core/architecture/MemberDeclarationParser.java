package com.rag.common.core.architecture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Java source text into top-level member declarations (constructors and
 * methods) for the test-only member gate. Deliberately lexical: the gate needs
 * only names, arities and line numbers, and must run over every module's
 * sources without a parser dependency.
 */
final class MemberDeclarationParser {

    private static final Set<String> CONTROL_WORDS = Set.of(
            "return", "new", "throw", "assert", "case", "else", "if", "for", "while", "switch",
            "catch", "try", "yield");
    private static final int MEMBER_INDENT = 4;
    private static final int MAX_SIGNATURE_LINES = 12;

    private MemberDeclarationParser() {
    }

    record Source(Path path, String pkg, String className, String text) {
    }

    record Member(Source source, String name, int arity, boolean ctor, int line) {
    }

    static List<Member> members(Source src) {
        List<Member> out = new ArrayList<>();
        String[] lines = src.text().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (indent(raw) != MEMBER_INDENT) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("private ") || trimmed.startsWith("/")
                    || trimmed.startsWith("*") || trimmed.startsWith("@")
                    || trimmed.startsWith("{") || trimmed.startsWith("}")) continue;
            if (previousNonBlank(lines, i).trim().startsWith("@")) continue;
            int sameLineOpen = trimmed.indexOf('(');
            if (sameLineOpen < 0) continue;
            String head = trimmed.substring(0, sameLineOpen).trim();
            String name = lastIdentifier(head);
            if (name == null || head.contains("=") || isControl(head)) continue;
            if (isRecordCtor(src.text(), name)) continue;
            boolean ctor = name.equals(src.className());
            if (!ctor && !looksLikeMethod(head)) continue;
            String signature = accumulate(lines, i);
            int open = signature.indexOf('(');
            int close = matchingParen(signature, open);
            if (close < 0) continue;
            out.add(new Member(src, name, parameterCount(signature.substring(open + 1, close)), ctor, i + 1));
        }
        return out;
    }

    private static String accumulate(String[] lines, int start) {
        var signature = new StringBuilder(lines[start].trim());
        for (int i = start + 1; i < Math.min(lines.length, start + MAX_SIGNATURE_LINES); i++) {
            if (balanced(signature)) break;
            signature.append(' ').append(lines[i].trim());
        }
        return signature.toString();
    }

    private static boolean balanced(CharSequence text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '(') depth++;
            else if (text.charAt(i) == ')') depth--;
        }
        return depth <= 0;
    }

    private static boolean isRecordCtor(String text, String name) {
        return Pattern.compile("\\brecord\\s+" + Pattern.quote(name) + "\\s*\\(").matcher(text).find();
    }

    private static boolean looksLikeMethod(String head) {
        return head.indexOf(' ') > 0;
    }

    private static boolean isControl(String head) {
        return CONTROL_WORDS.contains(head.split("\\s+", 2)[0]);
    }

    private static String lastIdentifier(String head) {
        Matcher matcher = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$").matcher(head);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int matchingParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int parameterCount(String params) {
        if (params.isBlank()) return 0;
        int depth = 0;
        int commas = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<' || c == '(' || c == '[') depth++;
            else if (c == '>' || c == ')' || c == ']') depth--;
            else if (c == ',' && depth == 0) commas++;
        }
        return commas + 1;
    }

    private static int indent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    private static String previousNonBlank(String[] lines, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) return lines[i];
        }
        return "";
    }

    static int skipLiteral(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            if (text.charAt(i) == '\\') i++;
            else if (text.charAt(i) == quote) return i;
        }
        return text.length() - 1;
    }
}
