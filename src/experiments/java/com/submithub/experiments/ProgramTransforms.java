package com.submithub.experiments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 표절 위장 변형 생성기. 원본 프로그램에 들어 있는 표시(//@rename, //@swap-*, //@method-*, //@insert)를 읽어
 * "의미(출력)를 바꾸지 않는" 변형을 자동으로 만든다. 표시는 결과물에서 모두 지운다.
 *
 * 표시 규칙
 *  - 첫 줄 `//@rename a,b,c`: 이름을 바꿔도 안전한 식별자 목록 (지역 변수·메서드·클래스)
 *  - `//@swap-start` ~ `//@swap-end`: 사이의 줄들은 서로 독립적인 문장이라 순서를 바꿔도 된다
 *  - `//@method-start` ~ `//@method-end`: 메서드 하나. 연속된 메서드들은 순서를 바꿔도 된다
 *  - `//@insert`: 문장 경계. 쓸모없는 코드를 넣어도 되는 곳
 */
final class ProgramTransforms {

    enum Type {
        RENAME("rename", "식별자 변경"),
        COMMENT_ADD("comment-add", "주석 추가"),
        COMMENT_REMOVE("comment-remove", "주석 삭제"),
        FORMAT("format", "공백·줄바꿈 변경"),
        SWAP_STATEMENTS("swap-stmts", "독립 문장 순서 변경"),
        SWAP_METHODS("swap-methods", "메서드 순서 변경"),
        DEAD_CODE("dead-code", "쓸모없는 코드 삽입"),
        LOOPS("loops", "for↔while 변환"),
        COMBINED("combined", "복합(전부 적용)");

        final String id;
        final String label;

        Type(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private ProgramTransforms() {
    }

    private static final Pattern MARKER_LINE = Pattern.compile("^\\s*//@.*$");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern CANONICAL_FOR =
            Pattern.compile("^(\\s*)for \\(int (\\w+) = ([^;]+); ([^;]+); (\\w+)(\\+\\+|--)\\) \\{\\s*$");
    private static final Pattern WHILE_LINE = Pattern.compile("^(\\s*)while \\((.*)\\) \\{\\s*$");

    private static final String[] WORDS = {"alpha", "bravo", "carbon", "delta", "echo", "fox", "gamma", "hotel", "india",
            "jade", "kilo", "lima", "mango", "nova", "oscar", "piano", "quartz", "river", "sigma", "tango", "umber",
            "violet", "whale", "xenon", "yodel", "zebra", "amber", "basil", "cedar", "dune", "ember", "flint", "grove",
            "harbor", "iris", "jungle", "karma", "lunar", "maple", "nectar", "opal", "pearl", "quill", "raven",
            "stone", "tulip", "urban", "vapor", "willow", "pixel"};

    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "var");

    private static final String[] COMMENTS = {"// 여기서 값을 갱신한다", "// check this", "// TODO: 나중에 정리", "// 반복 처리",
            "// 결과 확인", "// edge case", "// 임시 변수", "// 다음 단계로", "// 먼저 초기화", "// 주의: 범위"};

    // ---------- 진입점 ----------

    /** 표시를 모두 지운 원본 (데이터셋의 원본 프로그램) */
    static String clean(String source) {
        return stripMarkers(source);
    }

    static String apply(Type type, String source, long seed) {
        Random rnd = new Random(seed);
        String s = source;
        switch (type) {
            case RENAME -> s = rename(s, rnd);
            case COMMENT_ADD -> s = addComments(s, rnd);
            case COMMENT_REMOVE -> s = removeComments(s);
            case FORMAT -> s = reformat(s);
            case SWAP_STATEMENTS -> s = swapStatements(s, rnd);
            case SWAP_METHODS -> s = swapMethods(s, rnd);
            case DEAD_CODE -> s = insertDeadCode(s, rnd);
            case LOOPS -> s = convertLoops(s);
            case COMBINED -> {
                s = swapStatements(s, rnd);
                s = swapMethods(s, rnd);
                s = insertDeadCode(s, rnd);
                s = rename(s, rnd);
                s = convertLoops(s);
                s = removeComments(s);
                s = addComments(s, rnd);
                s = reformat(s);
            }
        }
        return stripMarkers(s);
    }

    // ---------- 공통 도구 ----------

    static String stripMarkers(String s) {
        List<String> out = new ArrayList<>();
        for (String line : s.split("\n", -1)) {
            if (!MARKER_LINE.matcher(line).matches()) {
                out.add(line);
            }
        }
        return String.join("\n", out);
    }

    private static List<String> lines(String s) {
        return new ArrayList<>(Arrays.asList(s.split("\n", -1)));
    }

    private static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    private static String indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    /** 코드 부분(주석·문자열·문자 리터럴 제외)에만 f 를 적용한다 */
    static String mapCode(String code, UnaryOperator<String> f) {
        StringBuilder out = new StringBuilder();
        StringBuilder buf = new StringBuilder();
        int n = code.length();
        int i = 0;
        while (i < n) {
            char c = code.charAt(i);
            char next = i + 1 < n ? code.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                out.append(f.apply(buf.toString()));
                buf.setLength(0);
                int end = code.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(code, i, end);
                i = end;
            } else if (c == '/' && next == '*') {
                out.append(f.apply(buf.toString()));
                buf.setLength(0);
                int end = code.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(code, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                out.append(f.apply(buf.toString()));
                buf.setLength(0);
                int j = i + 1;
                while (j < n && code.charAt(j) != c) {
                    if (code.charAt(j) == '\\') {
                        j++;
                    }
                    j++;
                }
                j = Math.min(n, j + 1);
                out.append(code, i, j);
                i = j;
            } else {
                buf.append(c);
                i++;
            }
        }
        out.append(f.apply(buf.toString()));
        return out.toString();
    }

    /** 한 줄에서 코드 부분의 { 와 } 개수 차이 */
    private static int braceDelta(String line) {
        int[] delta = {0};
        mapCode(line, seg -> {
            for (int i = 0; i < seg.length(); i++) {
                if (seg.charAt(i) == '{') {
                    delta[0]++;
                } else if (seg.charAt(i) == '}') {
                    delta[0]--;
                }
            }
            return seg;
        });
        return delta[0];
    }

    // ---------- 식별자 변경 ----------

    private static String rename(String source, Random rnd) {
        String first = source.split("\n", 2)[0].trim();
        if (!first.startsWith("//@rename ")) {
            return source;
        }
        List<String> names = Arrays.stream(first.substring("//@rename ".length()).split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();

        for (String forbidden : List.of("out", "length", "println")) {
            if (names.contains(forbidden)) {
                throw new IllegalArgumentException("JDK 멤버 이름과 겹쳐서 바꿀 수 없다: " + forbidden);
            }
        }
        Set<String> used = new HashSet<>();
        Matcher m = IDENT.matcher(source);
        while (m.find()) {
            used.add(m.group());
        }
        used.addAll(JAVA_KEYWORDS);

        Map<String, String> map = new HashMap<>();
        for (String name : names) {
            String candidate;
            do {
                String word = WORDS[rnd.nextInt(WORDS.length)] + (10 + rnd.nextInt(90));
                candidate = Character.isUpperCase(name.charAt(0))
                        ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
            } while (used.contains(candidate));
            used.add(candidate);
            map.put(name, candidate);
        }
        return mapCode(source, seg -> {
            Matcher mm = IDENT.matcher(seg);
            StringBuilder sb = new StringBuilder();
            while (mm.find()) {
                mm.appendReplacement(sb, Matcher.quoteReplacement(map.getOrDefault(mm.group(), mm.group())));
            }
            mm.appendTail(sb);
            return sb.toString();
        });
    }

    // ---------- 주석 ----------

    /** 한 줄에서 줄 주석과 블록 주석을 지운다 (여러 줄에 걸친 블록 주석은 쓰지 않는다) */
    private static String stripLineComments(String line) {
        // mapCode 가 주석을 건드리지 않으므로, 주석 시작 위치를 직접 찾는다
        int n = line.length();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            char next = i + 1 < n ? line.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                break;
            } else if (c == '/' && next == '*') {
                int end = line.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && line.charAt(j) != c) {
                    if (line.charAt(j) == '\\') {
                        j++;
                    }
                    j++;
                }
                j = Math.min(n, j + 1);
                out.append(line, i, j);
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString().stripTrailing();
    }

    private static String removeComments(String source) {
        List<String> out = new ArrayList<>();
        for (String line : lines(source)) {
            String stripped = stripLineComments(line);
            boolean commentOnly = stripped.isBlank() && !line.isBlank();
            if (!commentOnly) {
                out.add(stripped);
            }
        }
        return join(out);
    }

    private static String addComments(String source, Random rnd) {
        List<String> out = new ArrayList<>();
        out.add("/* 학생 과제 제출물 */");
        for (String line : lines(source)) {
            String t = line.trim();
            boolean statement = !t.isEmpty() && !t.startsWith("//") && !t.startsWith("/*") && !t.startsWith("*")
                    && (t.endsWith(";") || t.endsWith("{"));
            if (statement && rnd.nextDouble() < 0.45) {
                out.add(indentOf(line) + COMMENTS[rnd.nextInt(COMMENTS.length)]);
            }
            if (statement && t.endsWith(";") && !t.contains("//") && rnd.nextDouble() < 0.2) {
                out.add(line + " // ok");
            } else {
                out.add(line);
            }
        }
        return join(out);
    }

    // ---------- 공백·줄바꿈 ----------

    /** 들여쓰기를 탭으로, 여는 중괄호를 다음 줄로(Allman), 빈 줄을 모두 제거 */
    private static String reformat(String source) {
        List<String> out = new ArrayList<>();
        for (String raw : lines(source)) {
            if (raw.isBlank()) {
                continue;
            }
            String indent = indentOf(raw).replace("    ", "\t");
            String t = raw.trim();
            if (t.startsWith("//") || t.startsWith("/*") || !t.endsWith("{") || t.equals("{")) {
                out.add(indent + t);
                continue;
            }
            String body = t.substring(0, t.length() - 1).stripTrailing();
            if (body.startsWith("}")) {
                out.add(indent + "}");
                String rest = body.substring(1).strip();
                if (!rest.isEmpty()) {
                    out.add(indent + rest);
                }
            } else if (!body.isEmpty()) {
                out.add(indent + body);
            }
            out.add(indent + "{");
        }
        return join(out) + "\n";
    }

    // ---------- 독립 문장 / 메서드 순서 ----------

    private static String swapStatements(String source, Random rnd) {
        List<String> in = lines(source);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < in.size()) {
            String line = in.get(i);
            if (line.trim().equals("//@swap-start")) {
                int end = i + 1;
                List<String> group = new ArrayList<>();
                while (!in.get(end).trim().equals("//@swap-end")) {
                    group.add(in.get(end++));
                }
                shuffleDifferent(group, rnd);
                out.add(line);
                out.addAll(group);
                out.add(in.get(end));
                i = end + 1;
            } else {
                out.add(line);
                i++;
            }
        }
        return join(out);
    }

    private static <T> void shuffleDifferent(List<T> list, Random rnd) {
        if (list.size() < 2) {
            return;
        }
        List<T> original = new ArrayList<>(list);
        for (int attempt = 0; attempt < 20; attempt++) {
            Collections.shuffle(list, rnd);
            if (!list.equals(original)) {
                return;
            }
        }
        Collections.rotate(list, 1);
    }

    private static String swapMethods(String source, Random rnd) {
        List<String> in = lines(source);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < in.size()) {
            if (!in.get(i).trim().equals("//@method-start")) {
                out.add(in.get(i++));
                continue;
            }
            // 연속된 메서드 블록(사이에는 빈 줄만 있음)을 한 묶음으로 읽는다
            List<List<String>> blocks = new ArrayList<>();
            int blanksAfter = 0;
            while (i < in.size() && in.get(i).trim().equals("//@method-start")) {
                List<String> block = new ArrayList<>();
                while (!in.get(i).trim().equals("//@method-end")) {
                    block.add(in.get(i++));
                }
                block.add(in.get(i++));
                blocks.add(block);
                blanksAfter = 0;
                while (i < in.size() && in.get(i).isBlank()) {
                    blanksAfter++;
                    i++;
                }
            }
            shuffleDifferent(blocks, rnd);
            for (int b = 0; b < blocks.size(); b++) {
                out.addAll(blocks.get(b));
                if (b < blocks.size() - 1) {
                    out.add("");
                }
            }
            for (int k = 0; k < blanksAfter; k++) {
                out.add("");
            }
        }
        return join(out);
    }

    // ---------- 쓸모없는 코드 ----------

    private static String insertDeadCode(String source, Random rnd) {
        int[] counter = {0};
        List<String> out = new ArrayList<>();
        for (String line : lines(source)) {
            if (!line.trim().equals("//@insert")) {
                out.add(line);
                continue;
            }
            String indent = indentOf(line);
            List<Integer> picks = new ArrayList<>(List.of(0, 1, 2, 3, 4));
            Collections.shuffle(picks, rnd);
            for (int p = 0; p < 2; p++) {
                for (String snippetLine : snippet(picks.get(p), ++counter[0]).split("\n")) {
                    out.add(indent + snippetLine);
                }
            }
        }
        return join(out);
    }

    private static String snippet(int kind, int k) {
        return switch (kind) {
            case 0 -> "int total" + k + " = 0;\nfor (int step" + k + " = 0; step" + k + " < 3; step" + k + "++) {\n    total" + k + " += step" + k + ";\n}";
            case 1 -> "String note" + k + " = \"unused\";\nnote" + k + " = note" + k + " + note" + k + ".length();";
            case 2 -> "if (System.currentTimeMillis() < 0) {\n    System.out.println(\"never printed " + k + "\");\n}";
            case 3 -> "long stamp" + k + " = System.nanoTime();\nstamp" + k + " = stamp" + k + " ^ (stamp" + k + " >>> 3);";
            default -> "int[] spare" + k + " = new int[4];\nspare" + k + "[0] = spare" + k + ".length * 2;";
        };
    }

    // ---------- for ↔ while ----------

    private static String convertLoops(String source) {
        List<String> in = lines(source);
        return join(convert(in, 0, in.size()));
    }

    /** lines[from, to) 안의 반복문을 바꿔서 돌려준다. 단순 증가 for 는 while 로, while 은 빈 초기화/증감의 for 로 */
    private static List<String> convert(List<String> in, int from, int to) {
        List<String> out = new ArrayList<>();
        int i = from;
        while (i < to) {
            String line = in.get(i);
            Matcher f = CANONICAL_FOR.matcher(line);
            Matcher w = WHILE_LINE.matcher(line);
            if (f.matches() && f.group(2).equals(f.group(5))) {
                int end = blockEnd(in, i);
                boolean hasContinue = false;
                for (int k = i + 1; k < end; k++) {
                    if (in.get(k).matches(".*\\bcontinue\\b.*")) {
                        hasContinue = true;
                    }
                }
                if (!hasContinue) {
                    String ind = f.group(1);
                    out.add(ind + "{");
                    out.add(ind + "    int " + f.group(2) + " = " + f.group(3) + ";");
                    out.add(ind + "    while (" + f.group(4) + ") {");
                    for (String body : convert(in, i + 1, end)) {
                        out.add("    " + body);
                    }
                    out.add(ind + "        " + f.group(2) + f.group(6) + ";");
                    out.add(ind + "    }");
                    out.add(ind + "}");
                    i = end + 1;
                    continue;
                }
            }
            if (w.matches()) {
                int end = blockEnd(in, i);
                out.add(w.group(1) + "for (; " + w.group(2) + "; ) {");
                out.addAll(convert(in, i + 1, end));
                out.add(in.get(end));
                i = end + 1;
                continue;
            }
            out.add(line);
            i++;
        }
        return out;
    }

    /** lines[start] 가 { 로 끝나는 줄일 때, 짝이 되는 } 가 있는 줄의 번호 */
    private static int blockEnd(List<String> in, int start) {
        int depth = braceDelta(in.get(start));
        int i = start + 1;
        while (i < in.size()) {
            depth += braceDelta(in.get(i));
            if (depth <= 0) {
                return i;
            }
            i++;
        }
        throw new IllegalStateException("짝이 맞지 않는 중괄호: " + in.get(start));
    }
}
