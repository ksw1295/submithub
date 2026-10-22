//@rename adj,visited,dfs,countComponents,addEdge,vertex,neighbor,nodes,edges,components,u,v,limit,names
import java.util.ArrayList;
import java.util.List;

public class Main {
    static List<List<Integer>> adj = new ArrayList<>();
    static boolean[] visited;

    //@method-start
    static void addEdge(int u, int v) {
        adj.get(u).add(v);
        adj.get(v).add(u);
    }
    //@method-end

    //@method-start
    // 깊이 우선 탐색으로 vertex 와 연결된 모든 정점을 방문한다
    static void dfs(int vertex) {
        visited[vertex] = true;
        //@insert
        for (int i = 0; i < adj.get(vertex).size(); i++) {
            int neighbor = adj.get(vertex).get(i);
            if (!visited[neighbor]) {
                dfs(neighbor);
            }
        }
    }
    //@method-end

    //@method-start
    // 연결 요소의 개수를 센다
    static int countComponents(int nodes) {
        visited = new boolean[nodes];
        int components = 0;
        for (int v = 0; v < nodes; v++) {
            if (!visited[v]) {
                dfs(v);
                components++;
            }
        }
        return components;
    }
    //@method-end

    public static void main(String[] args) {
        //@swap-start
        int nodes = 7;
        String names = "graph-A";
        int limit = 100;
        //@swap-end
        for (int i = 0; i < nodes; i++) {
            adj.add(new ArrayList<>());
        }
        addEdge(0, 1);
        addEdge(1, 2);
        addEdge(3, 4);
        addEdge(5, 6);
        //@insert
        System.out.println(names + " components = " + countComponents(nodes) + " (limit " + limit + ")");
    }
}
