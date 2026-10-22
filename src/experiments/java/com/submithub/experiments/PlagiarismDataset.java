package com.submithub.experiments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 표절 검사 평가용 데이터셋.
 * 서로 다른 원본 프로그램(resources/programs/*.java)마다 "같은 계열"을 만든다:
 * 원본 1개 + 위장 변형 9개(단일 변형 8가지 + 복합 1개). 같은 계열끼리는 표절, 다른 계열끼리는 비표절이다.
 */
final class PlagiarismDataset {

    static final String ORIGINAL = "orig";

    record Program(String family, String variant, String code) {
        String id() {
            return family + "__" + variant;
        }
    }

    /**
     * 같은 과제를 서로 독립적으로 푼 계열 묶음(어려운 음성). 같은 묶음 안의 서로 다른 계열은 표절이 아니다.
     * 키는 계열 번호(파일 이름 앞 두 자리), 값은 과제 이름
     */
    static final Map<String, String> SAME_TASK = Map.ofEntries(
            Map.entry("01", "bubble-sort"), Map.entry("21", "bubble-sort"),
            Map.entry("07", "linked-list"), Map.entry("22", "linked-list"),
            Map.entry("08", "bfs-grid"), Map.entry("23", "bfs-grid"),
            Map.entry("13", "primes"), Map.entry("24", "primes"),
            Map.entry("06", "bracket-stack"), Map.entry("25", "bracket-stack"));

    private PlagiarismDataset() {
    }

    static List<Program> build(long seed) throws IOException, URISyntaxException {
        Path dir = Path.of(PlagiarismDataset.class.getClassLoader().getResource("programs").toURI());
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
        List<Program> programs = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".java", "");
            String source = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            programs.add(new Program(name, ORIGINAL, ProgramTransforms.clean(source)));
            for (ProgramTransforms.Type type : ProgramTransforms.Type.values()) {
                long variantSeed = seed * 1_000_003L + name.hashCode() * 31L + type.ordinal();
                programs.add(new Program(name, type.id, ProgramTransforms.apply(type, source, variantSeed)));
            }
        }
        return programs;
    }

    static String number(String family) {
        return family.substring(0, 2);
    }

    /** 두 계열이 같은 과제를 독립적으로 푼 것인지 */
    static boolean sameTask(String familyA, String familyB) {
        String a = SAME_TASK.get(number(familyA));
        return a != null && a.equals(SAME_TASK.get(number(familyB)));
    }
}
