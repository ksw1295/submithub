//@rename memo,fibMemo,fibTable,fibIter,table,prev,curr,next,limit,term,result,compare
public class Main {
    static long[] memo = new long[91];

    //@method-start
    // 메모이제이션 재귀
    static long fibMemo(int term) {
        if (term <= 1) {
            return term;
        }
        if (memo[term] != 0) {
            return memo[term];
        }
        memo[term] = fibMemo(term - 1) + fibMemo(term - 2);
        return memo[term];
    }
    //@method-end

    //@method-start
    // 표를 채우는 상향식 DP
    static long fibTable(int term) {
        if (term <= 1) {
            return term;
        }
        long[] table = new long[term + 1];
        table[1] = 1;
        //@insert
        for (int i = 2; i <= term; i++) {
            table[i] = table[i - 1] + table[i - 2];
        }
        return table[term];
    }
    //@method-end

    //@method-start
    // 변수 두 개만 쓰는 반복문
    static long fibIter(int term) {
        long prev = 0;
        long curr = 1;
        for (int i = 0; i < term; i++) {
            long next = prev + curr;
            prev = curr;
            curr = next;
        }
        return prev;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int limit = 50;
        String compare = "fib(%d) memo=%d table=%d iter=%d";
        //@swap-end
        for (int n = 10; n <= limit; n += 10) {
            long a = fibMemo(n);
            long b = fibTable(n);
            long c = fibIter(n);
            //@insert
            System.out.println(String.format(compare, n, a, b, c));
        }
    }
}
