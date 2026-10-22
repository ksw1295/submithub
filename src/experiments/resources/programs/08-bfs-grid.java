//@rename grid,rows,cols,dist,queue,front,back,nr,nc,dr,dc,d,bfs,inside,r,c,startR,startC,answer
import java.util.ArrayDeque;
import java.util.Arrays;

public class Main {
    //@swap-start
    static int[] dr = {1, -1, 0, 0};
    static int[] dc = {0, 0, 1, -1};
    //@swap-end

    //@method-start
    static boolean inside(int r, int c, int rows, int cols) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }
    //@method-end

    //@method-start
    // 격자(0 = 길, 1 = 벽)에서 시작점으로부터 각 칸까지의 최단 거리를 구한다
    static int[][] bfs(int[][] grid, int startR, int startC) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] dist = new int[rows][cols];
        for (int[] line : dist) {
            Arrays.fill(line, -1);
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startR, startC});
        dist[startR][startC] = 0;
        //@insert
        while (!queue.isEmpty()) {
            int[] now = queue.poll();
            for (int d = 0; d < 4; d++) {
                int nr = now[0] + dr[d];
                int nc = now[1] + dc[d];
                if (inside(nr, nc, rows, cols) && grid[nr][nc] == 0 && dist[nr][nc] == -1) {
                    dist[nr][nc] = dist[now[0]][now[1]] + 1;
                    queue.add(new int[]{nr, nc});
                }
            }
        }
        return dist;
    }
    //@method-end

    public static void main(String[] args) {
        int[][] grid = {
                {0, 0, 1, 0},
                {1, 0, 1, 0},
                {0, 0, 0, 0},
                {0, 1, 1, 0}
        };
        int[][] dist = bfs(grid, 0, 0);
        //@insert
        for (int r = 0; r < dist.length; r++) {
            System.out.println(Arrays.toString(dist[r]));
        }
        int answer = dist[3][3];
        System.out.println("shortest path to (3,3) = " + answer);
    }
}
