//@rename arr,low,high,pivot,mid,left,right,quickSort,partition,swap,tmp,data,printAll
import java.util.Arrays;

public class Main {
    //@method-start
    static void swap(int[] arr, int left, int right) {
        int tmp = arr[left];
        arr[left] = arr[right];
        arr[right] = tmp;
    }
    //@method-end

    //@method-start
    // 마지막 원소를 기준값으로 삼아 분할한다
    static int partition(int[] arr, int low, int high) {
        int pivot = arr[high];
        int mid = low - 1;
        //@insert
        for (int left = low; left < high; left++) {
            if (arr[left] <= pivot) {
                mid++;
                swap(arr, mid, left);
            }
        }
        swap(arr, mid + 1, high);
        return mid + 1;
    }
    //@method-end

    //@method-start
    static void quickSort(int[] arr, int low, int high) {
        if (low < high) {
            int right = partition(arr, low, high);
            quickSort(arr, low, right - 1);
            quickSort(arr, right + 1, high);
        }
    }
    //@method-end

    //@method-start
    static void printAll(String title, int[] arr) {
        System.out.println(title + " " + Arrays.toString(arr));
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] data = {10, 80, 30, 90, 40, 50, 70, 20};
        String tag = "quick";
        int round = 1;
        //@swap-end
        printAll(tag + " start " + round, data);
        //@insert
        quickSort(data, 0, data.length - 1);
        printAll("sorted", data);
    }
}
