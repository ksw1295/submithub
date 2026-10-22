//@rename values,size,minIndex,temp,i,j,selectionSort,findMin,show,numbers,text
import java.util.Arrays;

public class Main {
    //@method-start
    // start 이후에서 가장 작은 값의 위치를 찾는다
    static int findMin(int[] values, int start) {
        int minIndex = start;
        for (int j = start + 1; j < values.length; j++) {
            if (values[j] < values[minIndex]) {
                minIndex = j;
            }
        }
        return minIndex;
    }
    //@method-end

    //@method-start
    // 선택 정렬
    static void selectionSort(int[] values) {
        int size = values.length;
        //@insert
        for (int i = 0; i < size - 1; i++) {
            int minIndex = findMin(values, i);
            if (minIndex != i) {
                int temp = values[i];
                values[i] = values[minIndex];
                values[minIndex] = temp;
            }
        }
    }
    //@method-end

    //@method-start
    static void show(String text, int[] values) {
        System.out.println(text + ": " + Arrays.toString(values));
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[] numbers = {64, 25, 12, 22, 11, 90, 3};
        String text = "selection";
        //@swap-end
        show(text + " before", numbers);
        selectionSort(numbers);
        //@insert
        show(text + " after", numbers);
    }
}
