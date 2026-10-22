//@rename arr,n,tmp,swapped,i,j,bubbleSort,printArray,data,count,sb,label
import java.util.Arrays;

public class Main {
    //@method-start
    // 배열의 내용을 한 줄로 출력한다
    static void printArray(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) {
                sb.append(" ");
            }
        }
        System.out.println(sb);
    }
    //@method-end

    //@method-start
    // 버블 정렬: 이웃한 두 원소를 비교하며 큰 값을 뒤로 보낸다
    static int bubbleSort(int[] arr) {
        int n = arr.length;
        int count = 0;
        //@insert
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                    swapped = true;
                    count++;
                }
            }
            if (!swapped) {
                break;
            }
        }
        return count;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] data = {5, 3, 9, 1, 7, 2, 8};
        String label = "bubble";
        int round = 1;
        //@swap-end
        System.out.println(label + " round " + round);
        printArray(data);
        int count = bubbleSort(data);
        //@insert
        printArray(data);
        System.out.println("swaps = " + count);
        System.out.println(Arrays.toString(data));
    }
}
