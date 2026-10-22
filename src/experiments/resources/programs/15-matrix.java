//@rename multiply,transpose,showMatrix,left,right,product,rowsA,colsA,colsB,acc,swapped,matrix,line,title,r,c,k
import java.util.Arrays;

public class Main {
    //@method-start
    // 행렬 곱셈
    static int[][] multiply(int[][] left, int[][] right) {
        int rowsA = left.length;
        int colsA = left[0].length;
        int colsB = right[0].length;
        int[][] product = new int[rowsA][colsB];
        //@insert
        for (int r = 0; r < rowsA; r++) {
            for (int c = 0; c < colsB; c++) {
                int acc = 0;
                for (int k = 0; k < colsA; k++) {
                    acc += left[r][k] * right[k][c];
                }
                product[r][c] = acc;
            }
        }
        return product;
    }
    //@method-end

    //@method-start
    static int[][] transpose(int[][] matrix) {
        int[][] swapped = new int[matrix[0].length][matrix.length];
        for (int r = 0; r < matrix.length; r++) {
            for (int c = 0; c < matrix[0].length; c++) {
                swapped[c][r] = matrix[r][c];
            }
        }
        return swapped;
    }
    //@method-end

    //@method-start
    static void showMatrix(String title, int[][] matrix) {
        System.out.println(title);
        for (int[] line : matrix) {
            System.out.println(Arrays.toString(line));
        }
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int[][] left = {{1, 2, 3}, {4, 5, 6}};
        int[][] right = {{7, 8}, {9, 10}, {11, 12}};
        //@swap-end
        showMatrix("A x B", multiply(left, right));
        //@insert
        showMatrix("transpose(A)", transpose(left));
    }
}
