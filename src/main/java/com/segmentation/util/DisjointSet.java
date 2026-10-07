package com.segmentation.util;

/**
 * High-performance Disjoint-Set (Union-Find) data structure with path compression
 * and union-by-rank heuristics. Runs near O(1) inverse Ackermann complexity per operation.
 * Used in graph-based segmentation (Felzenszwalb) and spatial connectivity enforcement.
 */
public class DisjointSet {
    private final int[] parent;
    private final int[] rank;
    private final int[] size;
    private int numSets;

    public DisjointSet(int n) {
        this.parent = new int[n];
        this.rank = new int[n];
        this.size = new int[n];
        this.numSets = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            rank[i] = 0;
            size[i] = 1;
        }
    }

    /**
     * Finds the representative root of element i with path compression.
     */
    public int find(int i) {
        int root = i;
        while (root != parent[root]) {
            root = parent[root];
        }
        // Path compression
        int curr = i;
        while (curr != root) {
            int nxt = parent[curr];
            parent[curr] = root;
            curr = nxt;
        }
        return root;
    }

    /**
     * Unites sets containing elements x and y using union-by-rank.
     * @return true if sets were merged, false if already in same set.
     */
    public boolean union(int x, int y) {
        int rootX = find(x);
        int rootY = find(y);
        if (rootX == rootY) {
            return false;
        }

        if (rank[rootX] < rank[rootY]) {
            parent[rootX] = rootY;
            size[rootY] += size[rootX];
        } else if (rank[rootX] > rank[rootY]) {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
        } else {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
            rank[rootX]++;
        }
        numSets--;
        return true;
    }

    public int getSize(int element) {
        return size[find(element)];
    }

    public int getNumSets() {
        return numSets;
    }
}
