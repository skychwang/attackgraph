package rldepgraph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.random.RandomDataGenerator;

import agent.AgentFactory;
import agent.Attacker;
import game.GameSimulationSpec;
import model.DependencyGraph;
import py4j.GatewayServer;
import rl.RLDefenderRawObservation;
import utils.DGraphUtils;
import utils.EncodingUtils;
import utils.JsonUtils;

/**
 * Wrapper for a game of depgraph, 
 * to be used by Py4J.
 * 
 * Assumes learning agent will select nodes to defend
 * one at a time, in a greedy fashion.
 * Game state indicates which nodes are currently in the
 * list to defend.
 * 
 * Requirements: Py4J,
 * https://www.py4j.org/install.html
 * https://www.py4j.org/download.html
 */
public final class DepgraphPy4JGreedy {

	/**
	 * Inner object that represents the game state.
	 */
	private RLGameSimulation sim;
	
	/**
	 * The likelihood that after each
	 * round of adding one node to the set to defend in an episode,
	 * the defender agent will not be allowed to add more nodes.
	 */
	private final double probGreedySelectionCutOff;
	
	/**
	 * The set of node IDs to be defended in a given episode.
	 */
	private final Set<Integer> nodesToDefend;
	
	/**
	 * Used to get random values for selection cutoff.
	 */
	private static final Random RAND = new Random();
	
	/**
	 * If true, adding the same node to nodesToDefend
	 * repeatedly in one turn loses the game.
	 * 
	 * Otherwise, doing so is equivalent to the "pass"
	 * move and lead to selecting the current nodesToDefend.
	 */
	private static final boolean LOSE_IF_REPEAT = false;
	
	/**
	 * Public constructor.
	 * 
	 * @param aProbGreedySelectionCutOff likelihood that after each
	 * round of adding one node to the set to defend in an episode,
	 * the defender agent will not be allowed to add more nodes.
	 */
	public DepgraphPy4JGreedy(final double aProbGreedySelectionCutOff) {
		if (aProbGreedySelectionCutOff < 0.0
			|| aProbGreedySelectionCutOff >= 1.0) {
			throw new IllegalArgumentException();
		}
		this.probGreedySelectionCutOff = aProbGreedySelectionCutOff;
		this.nodesToDefend = new HashSet<Integer>();
		setupEnvironment();
	}
	
	/**
	 * Entry method, used to set up the Py4J server.
	 * @param args not used
	 */
	public static void main(final String[] args) {
		final double probGreedySelectCutOff = 0.1;
		// set up Py4J server
		final GatewayServer gatewayServer =
			new GatewayServer(new DepgraphPy4JGreedy(probGreedySelectCutOff));
		gatewayServer.start();
		System.out.println("Gateway Server Started");
	}
	
	/**
	 * Load the graph and simulation specification, and initialize
	 * the attacker opponent and environment.
	 */
	private void setupEnvironment() {
		final String simspecFolderName = "simspecs";
		final String graphFolderName = "graphs";
		final GameSimulationSpec simSpec =
			JsonUtils.getSimSpecOrDefaults(simspecFolderName);	
		// Load graph
		String filePathName = graphFolderName + File.separator
			+ "RandomGraph" + simSpec.getNumNode()
			+ "N" + simSpec.getNumEdge() + "E" 
			+ simSpec.getNumTarget() + "T"
			+ simSpec.getGraphID() + JsonUtils.JSON_SUFFIX;
		DependencyGraph depGraph = DGraphUtils.loadGraph(filePathName);
				
		// Load players
		final String attackerString =
			JsonUtils.getAttackerString(simspecFolderName);
		final String attackerName =
			EncodingUtils.getStrategyName(attackerString);
		final Map<String, Double> attackerParams =
			EncodingUtils.getStrategyParams(attackerString);
		
		Attacker attacker =
			AgentFactory.createAttacker(
				attackerName, attackerParams, simSpec.getDiscFact());
		RandomDataGenerator rng = new RandomDataGenerator();
		final int numTimeStep = simSpec.getNumTimeStep();
		final double discFact = simSpec.getDiscFact();
		this.sim = new RLGameSimulation(
			depGraph, attacker, rng, numTimeStep, discFact);
	}
	
	/**
	 * Get a new DepgraphPy4JGreedy object for Py4J.
	 * @return the DepgraphPy4JGreedy for Py4J to use.
	 */
	public static DepgraphPy4JGreedy getGame() {
		final double myProbGreedySelectionCutOff = 0.1;
		return new DepgraphPy4JGreedy(myProbGreedySelectionCutOff);
	}
	
	/**
	 * Reset the game (clear all actions and reset time steps left).
	 * 
	 * @return the state of the game as a list of doubles
	 */
	public List<Double> reset() {
		// clear the game state.
		this.sim.reset();
		// no nodesToDefend so far
		this.nodesToDefend.clear();
		
		return getDefObsAsListDouble();
	}

	/**
	 * Take a step based on the given action, represented as
	 * an integer.
	 * 
	 * Return a flat list representing, in order:
	 * the new defender observation state,
	 * the reward of the step for player taking action (in R-),
	 * whether the game is done (in {0, 1}).
	 * 
	 * Legal actions are (NODE_COUNT + 1) to pass, or any integer that
	 * is a node ID in the graph but is NOT in this.nodesToDefend.
	 * 
	 * If the action is illegal, do not update the game state,
	 * but consider the game as lost (i.e., minimal reward)
	 * and thus done (i.e., 1).
	 * 
	 * If the move is (NODE_COUNT + 1), 
	 * or if this.nodesToDefend is not empty and with 
	 * probability this.probGreedySelectionCutOff,
	 * the self agent (defender) and opponent (attacker)
	 * move simultaneously, where the defender protects this.nodesToDefend
	 * without adding any more items to it.
	 * 
	 * Otherwise, the agent's selected node ID is added to this.nodesToDefend
	 * and control returns to the defender without the attack making a move,
	 * the marginal reward is 0.0, and the time step does not advance.
	 * 
	 * @param action an Integer, the action to take.
	 * The action should be the index of a node to add to
	 * this.nodesToDefend, or (NODE_COUNT + 1) to indicate
	 * no more nodes should be added.
	 * @return the list representing the new game state,
	 * including the defender observation, reward, and whether the game is over,
	 * as one flat list.
	 */
	public List<Double> step(final Integer action) {
		if (action == null) {
			throw new IllegalArgumentException();
		}
		final List<Double> result = new ArrayList<Double>();
		final int nodeCount = this.sim.getNodeCount();
		if (action == (nodeCount + 1)
			|| (!this.nodesToDefend.isEmpty()
				&& RAND.nextDouble() < this.probGreedySelectionCutOff)
			|| (this.nodesToDefend.contains(action) && !LOSE_IF_REPEAT)
		) {
			// no more selections allowed.
			// either action was (nodeCount + 1) (pass),
			// or there is some nodesToDefend selected already
			// AND the random draw is below probGreedySelectionCutoff,
			// or the action is already in nodesToDefend AND
			// !LOSE_IF_REPEAT, so repeated selection counts as "pass".
			if (!this.sim.isValidMove(this.nodesToDefend)) {
				// illegal move. game is lost.
				final List<Double> defObs = getDefObsAsListDouble();
				// self player (defender) gets minimal reward for illegal move.
				final double reward = this.sim.getWorstRemainingReward();
				// game is over.
				final double isOver = 1.0;
				result.addAll(defObs);
				result.add(reward);
				result.add(isOver);
				return result;
			}
			
			// move is valid.
			this.sim.step(this.nodesToDefend);
			// reset nodesToDefend to empty set before next move.
			this.nodesToDefend.clear();
			
			final List<Double> defObs = getDefObsAsListDouble();
			final double reward = this.sim.getDefenderMarginalPayoff();
			double isOver = 0.0;
			if (this.sim.isGameOver()) {
				isOver = 1.0;
			}
			result.addAll(defObs);
			result.add(reward);
			result.add(isOver);
			return result;
		}
		
		// selection is allowed; will try to add to nodesToDefend.
		
		if (!this.sim.isValidId(action)
			|| (this.nodesToDefend.contains(action) && LOSE_IF_REPEAT)
		) {
			// illegal move. game is lost.
			final List<Double> defObs = getDefObsAsListDouble();
			// self player (defender) gets minimal reward for illegal move.
			final double reward = this.sim.getWorstRemainingReward();
			// game is over.
			final double isOver = 1.0;
			result.addAll(defObs);
			result.add(reward);
			result.add(isOver);
			return result;
		}

		// selection is valid and not a repeat. add to nodesToDefend.
		this.nodesToDefend.add(action);
		final List<Double> defObs = getDefObsAsListDouble();
		final double reward = 0.0; // no marginal reward for adding nodes to set
		final double isOver = 0.0; // game is not over.
		result.addAll(defObs);
		result.add(reward);
		result.add(isOver);
		return result;
	}
	
	/**
	 * Get a human-readable game state string.
	 * @return the string representing the human-readable game state.
	 */
	public String render() {
		return this.sim.getDefenderObservation().toString();
	}
	
	/**
	 * Observation list is of size 4 * N,
	 * where N is the number of nodes in the graph.
	 * 
	 * First N items are 1.0 if an attack was observed, else 0.0.
	 * Next N items are 1.0 if the node was defended, else 0.0.
	 * Next N items are 1.0 if the node is currently in set to defend, else 0.0.
	 * Next N items are 1.0 * timeStepsLeft.
	 * 
	 * @return get the defender observation as a list of Double
	 */
	private List<Double> getDefObsAsListDouble() {
		final List<Double> result = new ArrayList<Double>();
		final RLDefenderRawObservation defObs = 
			this.sim.getDefenderObservation();
		final Set<Integer> activeObservedIds = defObs.activeObservedIdSet();
		final Set<Integer> defendedIds = defObs.getDefendedIds();
		final int timeStepsLeft = defObs.getTimeStepsLeft();
		for (int i = 1; i <= this.sim.getNodeCount(); i++) {
			if (activeObservedIds.contains(i)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		for (int i = 1; i <= this.sim.getNodeCount(); i++) {
			if (defendedIds.contains(i)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		for (int i = 1; i <= this.sim.getNodeCount(); i++) {
			if (this.nodesToDefend.contains(i)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		for (int i = 1; i <= this.sim.getNodeCount(); i++) {
			result.add((double) timeStepsLeft);
		}
		return result;
	}
}