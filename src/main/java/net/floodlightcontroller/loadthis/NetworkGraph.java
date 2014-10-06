package net.floodlightcontroller.loadthis;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
/**
 * 
 * Class uses a graph to describe a network
 *
 */
public class NetworkGraph {
    private Map<Long, LinkedHashSet<Long>> map = new HashMap<Long, LinkedHashSet<Long>>();

    /**
     * Adding edge between node1 and node2
     * @param node1
     * 				SW DPID
     * @param node2
     * 				SW DPID
     */
    public void addEdge(long node1, long node2) {
        LinkedHashSet<Long> adjacent = map.get(node1);
        if(adjacent==null) {
            adjacent = new LinkedHashSet<Long>();
            map.put(node1, adjacent);
        }
        adjacent.add(node2);
    }

    public void addTwoWayVertex(long node1, long node2) {
        addEdge(node1, node2);
        addEdge(node2, node1);
    }

    public boolean isConnected(long node1, long node2) {
        Set adjacent = map.get(node1);
        if(adjacent==null) {
            return false;
        }
        return adjacent.contains(node2);
    }

    public LinkedList<Long> adjacentNodes(long last) {
        LinkedHashSet<Long> adjacent = map.get(last);
        if(adjacent==null) {
            return new LinkedList<Long>();
        }
        return new LinkedList<Long>(adjacent);
    }
}