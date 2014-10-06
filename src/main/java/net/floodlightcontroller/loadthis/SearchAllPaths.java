package net.floodlightcontroller.loadthis;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 
 * Class for searching all paths between to nodes.
 * All paths are stored in switchesRoutes.
 *
 */
public class SearchAllPaths {

	public static Map<Integer, LinkedList<Long>> switchesRoutes = new HashMap<Integer, LinkedList<Long>>();
	private static Integer routeNumber = new Integer(0);
	
	/**
	 * Run searching for all paths
	 * 
	 * @param graph
	 * 				Network graph
	 * @param visited
	 * 				Visited nodes. If you call this function it will contains a start node
	 * @param end
	 * 				End node
	 */
    public static void breadthFirst(NetworkGraph graph, LinkedList<Long> visited, long end) {
        LinkedList<Long> nodes = graph.adjacentNodes(visited.getLast());
        // examine adjacent nodes
        for (Long node : nodes) {
            if (visited.contains(node)) {
                continue;
            }
            if (node.equals(end)) {
                visited.add(node);
                LinkedList<Long> clonedVisited = (LinkedList<Long>) visited.clone();
                switchesRoutes.put(routeNumber, clonedVisited);
                routeNumber++;
                printPath(visited);
                visited.removeLast();
                break;
            }
        }
        // in breadth-first, recursion needs to come after visiting adjacent nodes
        for (Long node : nodes) {
            if (visited.contains(node) || node.equals(end)) {
                continue;
            }
            visited.addLast(node);
            breadthFirst(graph, visited, end);
            visited.removeLast();
        }
    }

    /**
     * Print last founded wpath
     * @param visited
     */
    private static void printPath(LinkedList<Long> visited) {
        for (Long node : visited) {
            System.out.print(node);
            System.out.print(" ");
        }
        System.out.println();
    }
    
    /**
     * Clears static fields in class
     */
    public static void clearSwitchesRoutes(){
    	switchesRoutes.clear();
    	routeNumber = 0;
    }
}