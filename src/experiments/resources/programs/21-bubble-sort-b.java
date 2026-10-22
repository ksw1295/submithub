//@rename a,end,lastSwap,x,y,exchange,sortBubble,joinAll,nums,tag,passes
import java.util.Arrays;
import java.util.stream.Collectors;

public class Main {
    //@method-start
    // 인접한 두 칸의 값을 XOR 로 맞바꾼다
    static void exchange(int[] a, int x, int y) {
        a[x] = a[x] ^ a[y];
        a[y] = a[x] ^ a[y];
        a[x] = a[x] ^ a[y];
    }
    //@method-end

    //@method-start
    // 마지막으로 자리를 바꾼 위치까지만 다시 훑는 버블 정렬
    static int sortBubble(int[] a) {
        int end = a.length - 1;
        int passes = 0;
        //@insert
        while (end > 0) {
            int lastSwap = 0;
            for (int x = 0; x < end; x++) {
                if (a[x] > a[x + 1]) {
                    exchange(a, x, x + 1);
                    lastSwap = x;
                }
            }
            end = lastSwap;
            passes++;
        }
        return passes;
    }
    //@method-end

    //@method-start
    static String joinAll(int[] a) {
        return Arrays.stream(a).boxed().map(String::valueOf).collect(Collectors.joining(", ", "{", "}"));
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] nums = {42, 7, 19, 3, 88, 21, 5};
        String tag = "bubble-b";
        //@swap-end
        System.out.println(tag + " in  " + joinAll(nums));
        int passes = sortBubble(nums);
        //@insert
        System.out.println("out " + joinAll(nums) + " after " + passes + " passes");
    }
}
