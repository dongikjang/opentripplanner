/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.algorithm;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opentripplanner.routing.algorithm.strategies.ExtraEdgesStrategy;
import org.opentripplanner.routing.algorithm.strategies.RemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.algorithm.strategies.SkipTraverseResultStrategy;
import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.PatternBoard;
import org.opentripplanner.routing.pqueue.BinHeap;
import org.opentripplanner.routing.pqueue.OTPPriorityQueue;
import org.opentripplanner.routing.pqueue.OTPPriorityQueueFactory;
import org.opentripplanner.routing.spt.BasicShortestPathTree;
import org.opentripplanner.routing.spt.MultiShortestPathTree;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.spt.ShortestPathTreeFactory;

/**
 * Find the shortest path between graph vertices using A*.
 */
public class GenericAStar {

    private boolean _verbose = false;
    
    private ShortestPathTreeFactory _shortestPathTreeFactory;

    private SkipTraverseResultStrategy _skipTraversalResultStrategy;

    private SearchTerminationStrategy _searchTerminationStrategy;
    
    public void setShortestPathTreeFactory(ShortestPathTreeFactory shortestPathTreeFactory) {
        _shortestPathTreeFactory = shortestPathTreeFactory;
    }

    public void setSkipTraverseResultStrategy(SkipTraverseResultStrategy skipTraversalResultStrategy) {
        _skipTraversalResultStrategy = skipTraversalResultStrategy;
    }

    public void setSearchTerminationStrategy(SearchTerminationStrategy searchTerminationStrategy) {
        _searchTerminationStrategy = searchTerminationStrategy;
    }

    /**
     * Plots a path on graph from origin to target, DEPARTING at the time given in state and with
     * the options options.
     * 
     * @param graph
     * @param origin
     * @param target
     * @param time 
     * @param options
     * @return the shortest path, or null if none is found
     */
    public ShortestPathTree getShortestPathTree(Graph graph, Vertex origin, Vertex target, long time, TraverseOptions options) {

        if (origin == null || target == null) {
            return null;
        }

        State s0;
        if (options.isArriveBy()) {
            s0 = new State(time, target, options);
        	target = origin;
        	origin = s0.getVertex();
        } else {
            s0 = new State(time, origin, options);
        }
        // from now on, target means "where this search will terminate"
        // not "the end of the trip from the user's perspective".
        
        ShortestPathTree spt = createShortestPathTree(s0, options);

        options.setTransferTable(graph.getTransferTable());

        /**
         * Populate any extra edges
         */
        final ExtraEdgesStrategy extraEdgesStrategy = options.extraEdgesStrategy;
        Map<Vertex, List<Edge>> extraEdges = new HashMap<Vertex, List<Edge>>();
        // conditional could be eliminated by placing this before the o/d swap above
        if (options.isArriveBy()) {
            extraEdgesStrategy.addIncomingEdgesForOrigin(extraEdges, origin);
            extraEdgesStrategy.addIncomingEdgesForTarget(extraEdges, target);
        } else {
            extraEdgesStrategy.addOutgoingEdgesForOrigin(extraEdges, origin);
            extraEdgesStrategy.addOutgoingEdgesForTarget(extraEdges, target);
        }

        if (extraEdges.isEmpty())
            extraEdges = Collections.emptyMap();

        final RemainingWeightHeuristic heuristic = options.remainingWeightHeuristic;

        double initialWeight = heuristic.computeInitialWeight(s0, target);
        spt.add(s0);

        // Priority Queue
        OTPPriorityQueueFactory factory = BinHeap.FACTORY;
        OTPPriorityQueue<State> pq = factory.create(graph.getVertices().size() + extraEdges.size());
        // this would allow continuing a search from an existing state
        pq.insert(s0, s0.getWeight() + initialWeight); 

        options = options.clone();
        /** max walk distance cannot be less than distances to nearest transit stops */
        double minWalkDistance = origin.getDistanceToNearestTransitStop() + 
        						 target.getDistanceToNearestTransitStop();
        options.maxWalkDistance = Math.max(options.maxWalkDistance, minWalkDistance); 

        long computationStartTime = System.currentTimeMillis();
        long maxComputationTime = options.maxComputationTime;

        boolean exit = false; // Unused?

        /* the core of the A* algorithm */
        while (!pq.empty()) { // Until the priority queue is empty:

            if (exit)
                break;  // unused?

            if (_verbose) {
                double w = pq.peek_min_key();
                System.out.println("pq min key = " + w);
            }

            /**
             * Terminate the search prematurely if we've hit our computation wall.
             */
            if (maxComputationTime > 0) {
                if ((System.currentTimeMillis() - computationStartTime) > maxComputationTime) {
                    break;
                }
            }

            // get the lowest-weight state in the queue
            State u = pq.extract_min(); 
            // check that this state has not been dominated
            // and mark vertex as visited 
            if (! spt.visit(u)) 
            	continue; 
            Vertex u_vertex = u.getVertex();
            // Uncomment the following statement
            // to print out a CSV (actually semicolon-separated) 
            // list of visited nodes for display in  a GIS
            //System.out.println(u_vertex + ";" + u_vertex.getX() + ";" + u_vertex.getY() + ";" + u.getWeight());
            
            if (_verbose)
                System.out.println("   vertex " + u_vertex);

            /**
             * Should we terminate the search?
             */
            if (_searchTerminationStrategy != null) {
                if (!_searchTerminationStrategy.shouldSearchContinue(origin, target, u, spt,
                        options))
                    break;
            } else if (u_vertex == target) {
                return spt;
            }

            Collection<Edge> edges = getEdgesForVertex(graph, extraEdges, u_vertex, options);

            for (Edge edge : edges) {

            	if (edge instanceof PatternBoard && u.getNumBoardings() > options.maxTransfers)
                    continue;

                // Iterate over traversal results. When an edge leads nowhere (as indicated by
                // returning NULL), the iteration is over.
                for (State v = edge.traverse(u); v != null; v = v.getNextResult()) {
                	// Could be: for (State v : traverseEdge...)

// now handled in state editor                	
//                	double delta_w = v.getWeight() - u.getWeight(); 
//                    if (delta_w < 0) { // <0
//                        throw new NegativeWeightException(String.valueOf(delta_w)
//                        								  + " on edge " + edge);
//                    }
                    
                    if( _skipTraversalResultStrategy != null 
                        && _skipTraversalResultStrategy.shouldSkipTraversalResult(origin, target, u, v, spt, options))
                        continue;

                    double remaining_w = computeRemainingWeight(heuristic, v, target, options);
                    double estimate = v.getWeight() + remaining_w;

                    if (_verbose) {
                    	System.out.println("      edge " + edge);
                        System.out.println("      " + u.getWeight() + " -> " + v.getWeight() + "(w) + "
                                + remaining_w + "(heur) = " + estimate + " vert = " + v.getVertex());
                    }

                    if (estimate > options.maxWeight || isWorstTimeExceeded(v, options)) {
                        // too expensive to get here
                    } else {
                        if (spt.add(v)) {
                            pq.insert(v, estimate);
                        }
                    }
                }
            }
        }
        return spt;
    }

    private Collection<Edge> getEdgesForVertex(Graph graph, Map<Vertex, List<Edge>> extraEdges,
            Vertex vertex, TraverseOptions options) {

        if (options.isArriveBy())
            return GraphLibrary.getIncomingEdges(graph, vertex, extraEdges);
        else
            return GraphLibrary.getOutgoingEdges(graph, vertex, extraEdges);
    }

    private double computeRemainingWeight(final RemainingWeightHeuristic heuristic,
            State v, Vertex target, TraverseOptions options) {
    	// actually, the heuristic could figure this out from the TraverseOptions.
    	// set private member back=options.isArriveBy() on initial weight computation.
        if (options.isArriveBy())
            return heuristic.computeReverseWeight(v, target);
        else
            return heuristic.computeForwardWeight(v, target);
    }

    private boolean isWorstTimeExceeded(State v, TraverseOptions options) {
        if (options.isArriveBy())
            return v.getTime() < options.worstTime;
        else
            return v.getTime() > options.worstTime;
    }

    private ShortestPathTree createShortestPathTree(State init, TraverseOptions options) {

        // Return Tree
        ShortestPathTree spt = null;

        if (_shortestPathTreeFactory != null)
            spt = _shortestPathTreeFactory.create();

        if (spt == null) {
            if (options.getModes().getTransit()) {
                spt = new MultiShortestPathTree();
            	//if (options.useServiceDays)
                    options.setServiceDays(init.getTime());
            } else {
                spt = new BasicShortestPathTree();
            }
        }

        return spt;
    }
}
