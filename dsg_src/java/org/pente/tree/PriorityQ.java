package org.pente.tree;

import java.util.Comparator;;

public class PriorityQ {
    
    private int size;
    private Node[] data;
    private Comparator<Node> comp;
    
    
    
    public PriorityQ(Comparator<Node> c, int initialCapacity) {
        this.comp = c;
        data = new Node[initialCapacity];
        size = 0;
        java.util.PriorityQueue<Node> n = null;
    }
    
    private int left(int i) {
        return 2 * i;
    }
    private int right(int i) {
        return 2 * i + 1;
    }
    private int parent(int i) {
        return i / 2;
    }
    private void grow() {
        int newcap = data.length;
        if (size < newcap) // don't need to grow
            return;
        if (size == Integer.MAX_VALUE)
            throw new OutOfMemoryError();
        while (newcap <= size) {
            if (newcap >= Integer.MAX_VALUE / 2)  // avoid overflow
                newcap = Integer.MAX_VALUE;
            else
                newcap <<= 2;
        }
        Node[] newData= new Node[newcap];
        System.arraycopy(data, 0, newData, 0, data.length);
        data = newData;
    }
    
    private void fixUp(int k) {
        while (k > 1) {
            int j = parent(k);
            if (comp.compare(data[j], data[k]) <= 0) {
                break;
            }
            Node tmp = data[j];
            data[j] = data[k];
            data[k] = tmp;
            
            data[k].setHeapIndex(k);
            data[j].setHeapIndex(j);
            k = j;
        }
    }
    private void fixDown(int k) {
        int j;
        while ((j = left(k)) <= size && (j > 0)) {
            if (j < size && 
                comp.compare(data[j], data[j + 1]) > 0)
                j++; 
            if (comp.compare(data[k], data[j]) <= 0)
                break;
            Node tmp = data[j];
            data[j] = data[k];
            data[k] = tmp;
            new java.util.PriorityQueue();
            data[k].setHeapIndex(k);
            data[j].setHeapIndex(j);
            k = j;
        }
    }
    
    public Node max() {
        if (size == 0) {
            return null;
        }
        return data[1];
    }
    public void add(Node n) {
        if (n == null) {
            throw new NullPointerException();
        }
        size++;
        if (size >= data.length) {
            grow();
        }
        data[size] = n;
        n.setHeapIndex(size);
        fixUp(size);

//        if (!check()) {
//            System.err.println("check failed in add");
//        }
    }
    
    public void remove(Node n) {
        if (n == null) {
            throw new NullPointerException();
        }

        int i = n.getHeapIndex();
        n.setHeapIndex(0);

        // not in q
        if (i == 0 || size == 0) {
            //System.err.println("attempt to remove i = 0 or empty " + i + ", " + size);
            return;
        }
        if (i > size) {
            //System.err.println("attempt to remove > size " + i + ", " + size);
            return;
        }

//        if (data[i].getHash() != n.getHash()) {
//            System.err.println("problem hash doesn't match " + size);
//            for (int j = 1; j <= size; j++) {
//                if (data[j].getHash() == n.getHash()) {
//                    System.err.println("found it at index " + j);
//                }
//            }
//            return;
//        }
        
        Node moved = data[size];
        data[i] = moved;
        data[size--] = null;  // Drop extra ref to prevent memory leak
        if (i <= size) {
            moved.setHeapIndex(i);
            fixDown(i);
            if (data[i] == moved) {
                fixUp(i);
            }
        }

//        if (!check()) {
//            System.err.println("check failed in remove");
//        }
    }
    public void increaseKey(Node n) {
        if (n == null) {
            throw new NullPointerException();
        }
        int i = n.getHeapIndex();
        fixDown(i);
        
//        if (!check()) {
//            System.err.println("check failed in increaseKey");
//        }
    }

    public boolean isEmpty() {
        return size == 0;
    }
    public int size() {
        return size;
    }
    public void clear() {
        for (int i = 1; i <= size; i++) {
            data[i] = null;
        }
        size = 0;
    }
//    
//    private boolean check() {
//        for (int i = 1; i <= size; i++) {
//            if (data[i].getHeapIndex() != i) {
//                System.err.println("problem");
//                return false;
//            }
//        }
//        return check2(1);
//    }
//    private boolean check2(int i) {
//        if (i <= size) {
//            int l = left(i);
//            int r = right(i);
//            if (l <= size && comp.compare(data[i], data[l]) > 0) {
//                System.err.println("sort problem");
//                return false;
//            }
//            if (r <= size && comp.compare(data[i], data[r]) > 0) {
//                System.err.println("sort problem");
//                return false;
//            }
//            boolean re = check2(l);
//            re &= check2(r);
//            return re;
//        }
//        return true;
//    }
}
