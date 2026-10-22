//@rename sorted,target,lo,hi,mid,searchLoop,searchRec,found,index,probe,values,wanted,names
public class Main {
    //@method-start
    // 반복문으로 구현한 이진 탐색
    static int searchLoop(int[] sorted, int target) {
        int lo = 0;
        int hi = sorted.length - 1;
        //@insert
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (sorted[mid] == target) {
                return mid;
            } else if (sorted[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -1;
    }
    //@method-end

    //@method-start
    // 재귀로 구현한 이진 탐색
    static int searchRec(int[] sorted, int target, int lo, int hi) {
        if (lo > hi) {
            return -1;
        }
        int mid = (lo + hi) >>> 1;
        if (sorted[mid] == target) {
            return mid;
        }
        if (sorted[mid] < target) {
            return searchRec(sorted, target, mid + 1, hi);
        }
        return searchRec(sorted, target, lo, mid - 1);
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] values = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
        int[] wanted = {23, 7, 91};
        String names = "search";
        //@swap-end
        for (int i = 0; i < wanted.length; i++) {
            int a = searchLoop(values, wanted[i]);
            int b = searchRec(values, wanted[i], 0, values.length - 1);
            //@insert
            System.out.println(names + " " + wanted[i] + " -> loop " + a + ", recursive " + b);
        }
    }
}
