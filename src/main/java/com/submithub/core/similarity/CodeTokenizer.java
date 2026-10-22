package com.submithub.core.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 표절 검사용 토큰화.
 *
 * 변수 이름만 바꾸거나, 주석·공백·줄바꿈을 바꾸거나, 문자열/숫자 값을 바꾸는 식의
 * 흔한 "위장"을 무력화하기 위해 코드를 구조만 남긴 토큰열로 바꾼다.
 *
 *   int total = price * 3;   // 합계
 *   →  int I = I * N ;
 *
 * - 주석(//, #, /* *&#47;)과 공백은 버린다
 * - 식별자는 모두 "I", 숫자는 "N", 문자열/문자 리터럴은 "S"
 * - 키워드와 연산자·괄호는 그대로 둔다 (프로그램 구조를 나타내므로)
 *
 * Java, C/C++, Python, JavaScript 에서 공통으로 쓸 수 있도록 키워드를 합쳐 두었다.
 */
public final class CodeTokenizer {

    public record Token(String value, int line) {
    }

    private static final Set<String> KEYWORDS = Set.of(
            // 제어문
            "if", "else", "elif", "for", "while", "do", "switch", "case", "default", "break", "continue",
            "return", "try", "catch", "finally", "throw", "throws", "except", "raise", "with", "yield",
            "goto", "pass", "in", "is", "not", "and", "or", "lambda", "def", "async", "await",
            // 선언/타입
            "class", "interface", "enum", "struct", "union", "typedef", "extends", "implements", "new",
            "public", "private", "protected", "static", "final", "const", "var", "let", "void", "abstract",
            "int", "long", "short", "byte", "char", "float", "double", "boolean", "bool", "string", "String",
            "unsigned", "signed", "auto", "this", "self", "super", "import", "from", "package", "include",
            "function", "global", "nonlocal", "del", "assert", "instanceof", "typeof", "sizeof",
            // 값
            "true", "false", "null", "None", "True", "False", "nullptr", "undefined"
    );

    private CodeTokenizer() {
    }

    public static List<Token> tokenize(String code) {
        List<Token> tokens = new ArrayList<>();
        if (code == null) {
            return tokens;
        }
        int line = 1;
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);

            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // 한 줄 주석: //, #
            if ((c == '/' && i + 1 < n && code.charAt(i + 1) == '/') || c == '#') {
                while (i < n && code.charAt(i) != '\n') i++;
                continue;
            }
            // 여러 줄 주석
            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                i += 2;
                while (i < n && !(code.charAt(i) == '*' && i + 1 < n && code.charAt(i + 1) == '/')) {
                    if (code.charAt(i) == '\n') line++;
                    i++;
                }
                i = Math.min(n, i + 2);
                continue;
            }
            // 문자열 / 문자 리터럴 (Python 삼중 따옴표 포함)
            if (c == '"' || c == '\'' || c == '`') {
                int startLine = line;
                String triple = "" + c + c + c;
                if (code.startsWith(triple, i)) {
                    int end = code.indexOf(triple, i + 3);
                    end = end < 0 ? n : end + 3;
                    line += count(code, i, end, '\n');
                    i = end;
                } else {
                    i++;
                    while (i < n && code.charAt(i) != c && code.charAt(i) != '\n') {
                        if (code.charAt(i) == '\\') i++;
                        i++;
                    }
                    i = Math.min(n, i + 1);
                }
                tokens.add(new Token("S", startLine));
                continue;
            }
            // 숫자
            if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(code.charAt(i)) || code.charAt(i) == '.' || code.charAt(i) == '_')) {
                    i++;
                }
                tokens.add(new Token("N", line));
                continue;
            }
            // 식별자 / 키워드
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(code.charAt(i))) i++;
                String word = code.substring(start, i);
                tokens.add(new Token(KEYWORDS.contains(word) ? word : "I", line));
                continue;
            }
            // 연산자, 괄호 등 한 글자 기호
            tokens.add(new Token(String.valueOf(c), line));
            i++;
        }
        return tokens;
    }

    private static int count(String s, int from, int to, char ch) {
        int count = 0;
        for (int i = from; i < to && i < s.length(); i++) {
            if (s.charAt(i) == ch) count++;
        }
        return count;
    }
}
