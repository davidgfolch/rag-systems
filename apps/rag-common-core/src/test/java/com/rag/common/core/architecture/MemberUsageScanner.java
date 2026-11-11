package com.rag.common.core.architecture;

import com.rag.common.core.architecture.MemberDeclarationParser.Member;
import com.rag.common.core.architecture.MemberDeclarationParser.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Source-text reference analysis for the cross-module test-only member gate. A
 * classpath based ArchUnit rule cannot see callers in sibling modules, so this
 * walks the source trees instead (same approach as
 * {@link FileLengthArchitectureTest}). A member is test-only when production
 * sources never reference it but test sources do, and orphaned when nothing
 * references it at all.
 */
final class MemberUsageScanner {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:public |protected |abstract |final )*(?:interface|enum|record|@interface)\\b");
    private static final Pattern ACCESSOR = Pattern.compile("^(get|is|set)[A-Z]\\w*$");
    private static final Pattern SPRING_BEAN = Pattern.compile(
            "(?m)^@(?:Component|Service|Repository|Controller|RestController|Configuration)\\b");
    private static final Set<String> EXCLUDED_SUFFIXES = Set.of(
            "Application", "Controller", "Config", "Configuration", "DTO", "Request", "Response",
            "Entity", "Exception", "Handler");
    private static final Set<String> IGNORED_NAMES = Set.of(
            "toString", "equals", "hashCode", "main", "run", "of", "builder");

    private MemberUsageScanner() {
    }

    record Violation(Member member, int prodRefs, int testRefs) {

        String render() {
            var m = member;
            return String.format("%s#%s(%d args) at %s:%d - %s (%d production refs, %d test refs)",
                    m.source().className(), m.name(), m.arity(), m.source().path(), m.line(),
                    testRefs > 0 ? "test-only" : "orphaned", prodRefs, testRefs);
        }
    }

    static List<String> violations(Path root, Set<String> allowlist) throws IOException {
        List<Source> main = new ArrayList<>();
        List<Source> tests = new ArrayList<>();
        for (Path module : directories(root.resolve("apps"))) {
            main.addAll(sources(module.resolve("src/main/java")));
            tests.addAll(sources(module.resolve("src/test/java")));
        }
        List<String> out = new ArrayList<>();
        for (Source src : main) {
            if (excludedDeclaration(src)) continue;
            boolean springBean = SPRING_BEAN.matcher(src.text()).find();
            for (Member member : MemberDeclarationParser.members(src)) {
                if (member.ctor() && springBean) continue;
                if (IGNORED_NAMES.contains(member.name()) || ACCESSOR.matcher(member.name()).matches()) continue;
                if (allowlist.contains(memberKey(member))) continue;
                int prodRefs = localRefs(member) + refs(main, member, src);
                int testRefs = refs(tests, member, null);
                if (prodRefs == 0) out.add(new Violation(member, 0, testRefs).render());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    static String memberKey(Member member) {
        return member.source().className() + "#" + member.name() + "/" + member.arity();
    }

    private static int refs(List<Source> pool, Member member, Source exclude) {
        int total = 0;
        for (Source src : pool) {
            if (src != exclude) total += count(src.text(), member);
        }
        return total;
    }

    private static boolean excludedDeclaration(Source src) {
        if (src.pkg().contains(".domain.")) return true;
        for (String suffix : EXCLUDED_SUFFIXES) {
            if (src.className().endsWith(suffix)) return true;
        }
        return TOP_LEVEL_TYPE.matcher(src.text()).find();
    }

    private static int localRefs(Member member) {
        String text = member.source().text();
        if (member.ctor()) return constructorCalls(text, member.source().className(), member.arity());
        return Math.max(0, methodCalls(text, member.name(), member.arity()) - 1);
    }

    private static int count(String text, Member member) {
        if (member.ctor()) return constructorCalls(text, member.source().className(), member.arity());
        return methodCalls(text, member.name(), member.arity());
    }

    private static int constructorCalls(String text, String className, int arity) {
        Matcher matcher = Pattern.compile("\\bnew\\s+" + Pattern.quote(className) + "\\s*(?:<[^()]*>)?\\s*\\(")
                .matcher(text);
        int total = 0;
        while (matcher.find()) {
            if (argumentCount(text, matcher.end() - 1) == arity) total++;
        }
        return total;
    }

    private static int methodCalls(String text, String name, int arity) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(text);
        int total = 0;
        while (matcher.find()) {
            if (argumentCount(text, matcher.end() - 1) == arity) total++;
        }
        if (Pattern.compile("::\\s*" + Pattern.quote(name) + "\\b").matcher(text).find()) total++;
        return total;
    }

    private static int argumentCount(String text, int openParen) {
        int depth = 0;
        int commas = 0;
        boolean hasToken = false;
        for (int i = openParen + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = MemberDeclarationParser.skipLiteral(text, i);
                hasToken = true;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) return hasToken ? commas + 1 : 0;
                depth--;
            } else if (c == ',' && depth == 0) commas++;
            if (!Character.isWhitespace(c)) hasToken = true;
        }
        return 0;
    }

    private static List<Path> directories(Path parent) throws IOException {
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static List<Source> sources(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .map(MemberUsageScanner::source)
                    .toList();
        }
    }

    private static Source source(Path path) {
        try {
            String text = Files.readString(path);
            Matcher matcher = PACKAGE.matcher(text);
            String pkg = matcher.find() ? matcher.group(1) : "";
            String name = path.getFileName().toString().replace(".java", "");
            return new Source(path, pkg, name, text);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
