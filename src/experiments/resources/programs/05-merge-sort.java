//@rename source,start,end,middle,buffer,p,q,k,mergeSort,merge,numbers,report,result
import java.util.Arrays;

public class Main {
    //@method-start
    // 정렬된 두 구간 [start, middle], (middle, end] 를 합친다
    static void merge(int[] source, int start, int middle, int end) {
        int[] buffer = new int[end - start + 1];
        int p = start;
        int q = middle + 1;
        int k = 0;
        //@insert
        while (p <= middle && q <= end) {
            if (source[p] <= source[q]) {
                buffer[k++] = source[p++];
            } else {
                buffer[k++] = source[q++];
            }
        }
        while (p <= middle) {
            buffer[k++] = source[p++];
        }
        while (q <= end) {
            buffer[k++] = source[q++];
        }
        for (int i = 0; i < buffer.length; i++) {
            source[start + i] = buffer[i];
        }
    }
    //@method-end

    //@method-start
    static void mergeSort(int[] source, int start, int end) {
        if (start >= end) {
            return;
        }
        int middle = (start + end) / 2;
        mergeSort(source, start, middle);
        mergeSort(source, middle + 1, end);
        merge(source, start, middle, end);
    }
    //@method-end

    //@method-start
    static void report(String result, int[] source) {
        System.out.println(result + " -> " + Arrays.toString(source));
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] numbers = {38, 27, 43, 3, 9, 82, 10, 5};
        String label = "merge";
        int version = 2;
        //@swap-end
        report(label + " v" + version + " input", numbers);
        //@insert
        mergeSort(numbers, 0, numbers.length - 1);
        report("output", numbers);
    }
}
