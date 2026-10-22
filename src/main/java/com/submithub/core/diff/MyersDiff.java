package com.submithub.core.diff;

import com.submithub.core.text.LineSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Myers diff 알고리즘 (E. Myers, "An O(ND) Difference Algorithm and Its Variations", 1986).
 *
 * 기존 Swing 버전의 LCS 방식은 n×m 표를 만들어서 3,000줄짜리 파일 두 개만 비교해도
 * 9백만 칸(약 36MB)이 필요했다. Myers 방식은 시간 O((N+M)·D), 메모리 O(D²)
 * (D = 바뀐 줄 수)라서 과제처럼 "조금씩 고친" 버전 비교에 훨씬 유리하다.
 *
 * 추가로 앞뒤 공통 부분을 먼저 잘라내서 실제로 비교할 구간을 줄인다.
 * git diff 와 같은 계열의 알고리즘이다.
 */
public final class MyersDiff {

    private MyersDiff() {
    }

    public static DiffResult diff(String oldText, String newText) {
        return diff(oldText, newText, DiffOptions.DEFAULT);
    }

    public static DiffResult diff(String oldText, String newText, DiffOptions options) {
        return diff(LineSplitter.split(oldText), LineSplitter.split(newText), options);
    }

    public static DiffResult diff(List<String> oldLines, List<String> newLines, DiffOptions options) {
        String[] a = keys(oldLines, options);
        String[] b = keys(newLines, options);

        int n = a.length;
        int m = b.length;

        // 1) 공통 앞부분 / 뒷부분 잘라내기
        int prefix = 0;
        while (prefix < n && prefix < m && a[prefix].equals(b[prefix])) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < n - prefix && suffix < m - prefix
                && a[n - 1 - suffix].equals(b[m - 1 - suffix])) {
            suffix++;
        }

        List<DiffLine> result = new ArrayList<>(n + m);
        for (int i = 0; i < prefix; i++) {
            result.add(DiffLine.equal(i + 1, i + 1, newLines.get(i)));
        }

        // 2) 가운데 구간만 Myers
        List<int[]> ops = middle(a, prefix, n - suffix, b, prefix, m - suffix);
        for (int[] op : ops) {
            int type = op[0];
            int ai = op[1];
            int bi = op[2];
            if (type == EQ) {
                result.add(DiffLine.equal(ai + 1, bi + 1, newLines.get(bi)));
            } else if (type == DEL) {
                result.add(DiffLine.removed(ai + 1, oldLines.get(ai)));
            } else {
                result.add(DiffLine.added(bi + 1, newLines.get(bi)));
            }
        }

        for (int i = 0; i < suffix; i++) {
            int ai = n - suffix + i;
            int bi = m - suffix + i;
            result.add(DiffLine.equal(ai + 1, bi + 1, newLines.get(bi)));
        }
        return new DiffResult(groupChanges(result));
    }

    private static final int EQ = 0;
    private static final int DEL = 1;
    private static final int INS = 2;

    /**
     * a[aStart, aEnd) 와 b[bStart, bEnd) 를 비교해서 {종류, a 인덱스, b 인덱스} 목록을 돌려준다.
     */
    private static List<int[]> middle(String[] a, int aStart, int aEnd, String[] b, int bStart, int bEnd) {
        int n = aEnd - aStart;
        int m = bEnd - bStart;
        List<int[]> ops = new ArrayList<>();
        if (n == 0 && m == 0) {
            return ops;
        }
        if (n == 0) {
            for (int j = 0; j < m; j++) ops.add(new int[]{INS, -1, bStart + j});
            return ops;
        }
        if (m == 0) {
            for (int i = 0; i < n; i++) ops.add(new int[]{DEL, aStart + i, -1});
            return ops;
        }

        int max = n + m;
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        // trace.get(d) = d번째 단계를 시작하기 직전의 v 에서 k ∈ [-d-1, d+1] 구간만 복사한 것
        List<int[]> trace = new ArrayList<>();

        int finalD = -1;
        outer:
        for (int d = 0; d <= max; d++) {
            trace.add(Arrays.copyOfRange(v, offset - d - 1, offset + d + 2));
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];          // 아래로 이동 = 삽입
                } else {
                    x = v[offset + k - 1] + 1;      // 오른쪽 이동 = 삭제
                }
                int y = x - k;
                while (x < n && y < m && a[aStart + x].equals(b[bStart + y])) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    finalD = d;
                    break outer;
                }
            }
        }

        // 역추적
        int x = n;
        int y = m;
        for (int d = finalD; d >= 0; d--) {
            int[] snap = trace.get(d);
            int base = d + 1; // snap 안에서 k=0 의 위치
            int k = x - y;
            int prevK;
            if (k == -d || (k != d && snap[base + k - 1] < snap[base + k + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = d == 0 ? 0 : snap[base + prevK];
            int prevY = prevX - prevK;
            if (d == 0) {
                prevX = 0;
                prevY = 0;
            }
            while (x > prevX && y > prevY) {
                ops.add(new int[]{EQ, aStart + x - 1, bStart + y - 1});
                x--;
                y--;
            }
            if (d > 0) {
                if (x == prevX) {
                    ops.add(new int[]{INS, -1, bStart + y - 1});
                } else {
                    ops.add(new int[]{DEL, aStart + x - 1, -1});
                }
            }
            x = prevX;
            y = prevY;
        }
        java.util.Collections.reverse(ops);
        return ops;
    }

    /**
     * 연속된 변경 구간 안에서 삭제 줄을 먼저, 추가 줄을 나중에 오도록 정렬한다.
     * (git diff / GitHub 와 같은 표시 순서. 좌우 분할 화면에서 줄을 짝지을 때도 필요)
     */
    private static List<DiffLine> groupChanges(List<DiffLine> lines) {
        List<DiffLine> out = new ArrayList<>(lines.size());
        List<DiffLine> removed = new ArrayList<>();
        List<DiffLine> added = new ArrayList<>();
        for (DiffLine line : lines) {
            switch (line.type()) {
                case REMOVED -> removed.add(line);
                case ADDED -> added.add(line);
                case EQUAL -> {
                    out.addAll(removed);
                    out.addAll(added);
                    removed.clear();
                    added.clear();
                    out.add(line);
                }
            }
        }
        out.addAll(removed);
        out.addAll(added);
        return out;
    }

    private static String[] keys(List<String> lines, DiffOptions options) {
        String[] keys = new String[lines.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = options.key(lines.get(i));
        }
        return keys;
    }
}
