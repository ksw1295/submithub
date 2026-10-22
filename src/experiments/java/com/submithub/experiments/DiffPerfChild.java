package com.submithub.experiments;

import com.submithub.core.diff.DiffResult;
import com.submithub.core.diff.MyersDiff;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/**
 * 한 번의 측정을 별도 JVM 에서 한다 (힙 크기를 -Xmx 로 제한해서 메모리 부족을 실제로 재현하려고).
 * 사용: DiffPerfChild lcs|myers 줄수 변경비율 시드
 * 출력 한 줄: RESULT status=OK|OOM ms=중앙값 allocMB=할당량 changed=변경된 줄 수
 */
public final class DiffPerfChild {

    private DiffPerfChild() {
    }

    public static void main(String[] args) {
        String algo = args[0];
        int n = Integer.parseInt(args[1]);
        double ratio = Double.parseDouble(args[2]);
        long seed = Long.parseLong(args[3]);
        try {
            String[] texts = DiffData.generate(n, ratio, seed);
            int reps = n <= 2000 ? 7 : n <= 10000 ? 3 : 1;
            if (n <= 5000) {
                run(algo, texts);         // 예열
            }
            com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long id = Thread.currentThread().getId();
            double[] ms = new double[reps];
            long allocated = 0;
            int changed = 0;
            for (int i = 0; i < reps; i++) {
                long a0 = mx.getThreadAllocatedBytes(id);
                long t0 = System.nanoTime();
                changed = run(algo, texts);
                ms[i] = (System.nanoTime() - t0) / 1e6;
                if (i == 0) {
                    allocated = mx.getThreadAllocatedBytes(id) - a0;
                }
            }
            Arrays.sort(ms);
            System.out.printf("RESULT status=OK ms=%.3f allocMB=%.1f changed=%d%n", ms[reps / 2], allocated / 1048576.0, changed);
        } catch (OutOfMemoryError e) {
            System.out.println("RESULT status=OOM ms=-1 allocMB=-1 changed=-1");
        }
    }

    /** @return 추가+삭제된 줄 수 */
    static int run(String algo, String[] texts) {
        if (algo.equals("lcs")) {
            List<LcsDiff.DiffLine> lines = LcsDiff.diff(texts[0], texts[1]);
            int c = 0;
            for (LcsDiff.DiffLine l : lines) {
                if (l.type != LcsDiff.LineType.EQUAL) {
                    c++;
                }
            }
            return c;
        }
        DiffResult r = MyersDiff.diff(texts[0], texts[1]);
        return r.addedCount() + r.removedCount();
    }
}
