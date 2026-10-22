//@rename list,key,pos,i,insertionSort,isSorted,input,steps,ok
import java.util.Arrays;

public class Main {
    //@method-start
    // 삽입 정렬: 앞쪽은 항상 정렬된 상태로 유지한다
    static int insertionSort(int[] list) {
        int steps = 0;
        //@insert
        for (int i = 1; i < list.length; i++) {
            int key = list[i];
            int pos = i - 1;
            while (pos >= 0 && list[pos] > key) {
                list[pos + 1] = list[pos];
                pos--;
                steps++;
            }
            list[pos + 1] = key;
        }
        return steps;
    }
    //@method-end

    //@method-start
    // 정렬되었는지 검사
    static boolean isSorted(int[] list) {
        for (int i = 1; i < list.length; i++) {
            if (list[i - 1] > list[i]) {
                return false;
            }
        }
        return true;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] input = {31, 41, 59, 26, 53, 58, 97, 93};
        boolean ok = false;
        //@swap-end
        System.out.println("before: " + Arrays.toString(input));
        int steps = insertionSort(input);
        //@insert
        ok = isSorted(input);
        System.out.println("after : " + Arrays.toString(input));
        System.out.println("shifts = " + steps + ", sorted = " + ok);
    }
}
