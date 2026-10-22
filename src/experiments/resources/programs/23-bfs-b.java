//@rename maze,width,height,steps,fifo,head,tail,cell,nx,ny,shortest,show,dirX,dirY,dir,goal
public class Main {
    //@swap-start
    static final int[] dirX = {0, 1, 0, -1};
    static final int[] dirY = {-1, 0, 1, 0};
    //@swap-end

    //@method-start
    // 배열 두 개로 만든 큐(칸 번호 = y * width + x)로 너비 우선 탐색을 한다
    static int[] shortest(int[][] maze, int sx, int sy) {
        int height = maze.length;
        int width = maze[0].length;
        int[] steps = new int[width * height];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = Integer.MAX_VALUE;
        }
        int[] fifo = new int[width * height];
        int head = 0;
        int tail = 0;
        fifo[tail++] = sy * width + sx;
        steps[sy * width + sx] = 0;
        //@insert
        while (head < tail) {
            int cell = fifo[head++];
            int cx = cell % width;
            int cy = cell / width;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + dirX[dir];
                int ny = cy + dirY[dir];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                if (maze[ny][nx] == 1 || steps[ny * width + nx] != Integer.MAX_VALUE) {
                    continue;
                }
                steps[ny * width + nx] = steps[cell] + 1;
                fifo[tail++] = ny * width + nx;
            }
        }
        return steps;
    }
    //@method-end

    //@method-start
    static void show(int[] steps, int width) {
        for (int i = 0; i < steps.length; i++) {
            String text = steps[i] == Integer.MAX_VALUE ? " X" : String.format("%2d", steps[i]);
            System.out.print(text + ((i + 1) % width == 0 ? "\n" : " "));
        }
    }
    //@method-end

    public static void main(String[] args) {
        int[][] maze = {
                {0, 0, 1, 0},
                {1, 0, 1, 0},
                {0, 0, 0, 0},
                {0, 1, 1, 0}
        };
        int[] steps = shortest(maze, 0, 0);
        show(steps, 4);
        //@insert
        int goal = steps[3 * 4 + 3];
        System.out.println("goal reached in " + goal);
    }
}
