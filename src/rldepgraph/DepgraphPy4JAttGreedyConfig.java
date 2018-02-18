package rldepgraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.random.RandomDataGenerator;

import agent.AgentFactory;
import agent.Defender;
import game.GameSimulationSpec;
import model.AttackerAction;
import model.DependencyGraph;
import py4j.GatewayServer;
import rl.RLAttackerRawObservation;
import utils.DGraphUtils;
import utils.EncodingUtils;
import utils.JsonUtils;

/**
 * Wrapper for a game of depgraph, 
 * to be used by Py4J.
 * 
 * Assumes learning agent will select nodes or edges to attack
 * one at a time, in a greedy fashion.
 * Game state indicates which nodes or edges are currently in the
 * list to attack.
 * 
 * Requirements: Py4J,
 * https://www.py4j.org/install.html
 * https://www.py4j.org/download.html
 */
public final class DepgraphPy4JAttGreedyConfig {

	/**
	 * Inner object that represents the game state.
	 */
	private RLAttackerGameSimulation sim;
	
	/**
	 * The likelihood that after each
	 * round of adding one node to the set to defend in an episode,
	 * the defender agent will not be allowed to add more nodes.
	 */
	private final double probGreedySelectionCutOff;
	
	/**
	 * The set of AND node IDs to be attacked in a given episode.
	 */
	private final Set<Integer> nodesToAttack;
	
	/**
	 * The set of edge IDs (to OR nodes) to be attacked in a given episode.
	 */
	private final Set<Integer> edgesToAttack;
	
	/**
	 * Used to get random values for selection cutoff.
	 */
	private static final Random RAND = new Random();
	
	/**
	 * Maps action integers in {1, . . ., count(AND nodes)}
	 * to increasing indexes of AND nodes in the graph.
	 * (If there are no AND nodes, it will be empty.)
	 */
	private final Map<Integer, Integer> actionToAndNodeIndex;
	
	/**
	 * Maps action integers in 
	 * {count(AND nodes) + 1, . . .,
	 * count(AND nodes) + count(edges to OR nodes)}
	 * to increasing indexes of edges to OR nodes in the graph.
	 * (If there are no edges to OR nodes, it will be empty.)
	 */
	private final Map<Integer, Integer> actionToEdgeToOrNodeIndex;
	
	/**
	 * If true, adding the same node to nodesToAttack
	 * or edge to edgesToAttack
	 * repeatedly in one turn loses the game.
	 * 
	 * Otherwise, doing so is equivalent to the "pass"
	 * move and leads to selecting the current nodesToAttack
	 * and edgesToAttack.
	 */
	private static final boolean LOSE_IF_REPEAT = false;
	
	/**
	 * Lists weight of each defender type in the mixed strategy,
	 * in order matching defenders.
	 */
	private final List<Double> defenderWeights;
	
	/**
	 * Lists agent for each defender type of the mixed strategy,
	 * in order matching defenderWeights.
	 */
	private final List<Defender> defenders;

	/**
	 * Used to reply to getGame().
	 */
	private static DepgraphPy4JAttGreedyConfig singleton;
	
	/**
	 * Used to get the observation of the attacker.
	 */
	private AttackerAction attAction = null;
	
	/**
	 * Public constructor.
	 * 
	 * @param aProbGreedySelectionCutOff likelihood that after each
	 * round of adding one node or edge to the set to attack in an episode,
	 * the attacker agent will not be allowed to add more.
	 * @param simSpecFolderName the folder from which simulation_spec.json
	 * will be taken
	 * @param defMixedStratFileName the file from which the mixed
	 * strategy of the defender will be read
	 * @param graphFileName the name of the graph file to use
	 */
	private DepgraphPy4JAttGreedyConfig(
		final double aProbGreedySelectionCutOff,
		final String simSpecFolderName,
		final String defMixedStratFileName,
		final String graphFileName
	) {
		if (aProbGreedySelectionCutOff < 0.0
			|| aProbGreedySelectionCutOff >= 1.0
			|| simSpecFolderName == null
			|| defMixedStratFileName == null) {
			throw new IllegalArgumentException();
		}
		this.probGreedySelectionCutOff = aProbGreedySelectionCutOff;
		this.nodesToAttack = new HashSet<Integer>();
		this.edgesToAttack = new HashSet<Integer>();
		this.defenders = new ArrayList<Defender>();
		this.defenderWeights = new ArrayList<Double>();
		this.actionToAndNodeIndex = new HashMap<Integer, Integer>();
		this.actionToEdgeToOrNodeIndex = new HashMap<Integer, Integer>();
		
		final double discFact = setupEnvironment(
			simSpecFolderName, graphFileName);
		setupDefendersAndWeights(defMixedStratFileName, discFact);
		setupActionMaps();
	}
	
	/**
	 * Initialize the maps from action integers to the AND node ID
	 * or edge to OR node ID to attack.
	 * Actions {1, . . ., count(AND nodes)} refer to the AND nodes
	 * in increasing ID order.
	 * Actions 
	 * {count(AND nodes) + 1, . . ., count(AND nodes) + count(edges to OR node)}
	 * refer to the edges to OR nodes in increasing ID order.
	 */
	private void setupActionMaps() {
		final List<Integer> andNodeIds = this.sim.getAndNodeIds();
		for (int action = 1; action <= andNodeIds.size(); action++) {
			// will be skipped if no AND nodes
			this.actionToAndNodeIndex.put(action, andNodeIds.get(action - 1));
		}
		
		final List<Integer> edgeToOrNodeIds = this.sim.getEdgeToOrNodeIds();
		for (int action = andNodeIds.size() + 1;
			action <= andNodeIds.size() + edgeToOrNodeIds.size();
			action++
		) {
			// will be skipped if no edges to OR nodes
			this.actionToEdgeToOrNodeIndex.put(
				action, 
				edgeToOrNodeIds.get(action - andNodeIds.size() - 1)
			);
		}
	}

	/**
	 * Entry method, used to set up the Py4J server.
	 * @param args has 3 args: simSpecFolder, defMixedStratFile,
	 * and graphFileName
	 */
	public static void main(final String[] args) {
		final int argsCount = 3;
		if (args == null || args.length != argsCount) {
			throw new IllegalArgumentException(
		"Need 3 args: simSpecFolder, defMixedStratFile, graphFileName"
			);
		}
		final String simSpecFolderName = args[0];
		final String defMixedStratFileName = args[1];
		// RandomGraph30N100E2T1.json
		// SepLayerGraph0.json
		final String graphFileName = args[2];
		
		final double probGreedySelectCutOff = 0.1;
		// set up Py4J server
		singleton = new DepgraphPy4JAttGreedyConfig(
			probGreedySelectCutOff,
			simSpecFolderName,
			defMixedStratFileName,
			graphFileName
		);
		final GatewayServer gatewayServer = new GatewayServer(singleton);
		gatewayServer.start();
		System.out.println("Gateway Server Started");
	}
	
	/**
	 * Initialize defenders and defenderWeights from the given file.
	 * @param defMixedStratFileName a file name for the mixed strategy.
	 * The mixed strategy should have a defender type per line,
	 * with the type string followed by tab, followed by the weight as a double.
	 * @param discFact the discount factor of the game
	 */
	private void setupDefendersAndWeights(
		final String defMixedStratFileName,
		final double discFact
	) {
		this.defenders.clear();
		this.defenderWeights.clear();

		final List<String> lines = getLines(defMixedStratFileName);
		double totalWeight = 0.0;
		for (final String line: lines) {
			final String strippedLine = line.trim();
			if (strippedLine.length() > 0) {
				final String[] lineSplit = strippedLine.split("\t");
				if (lineSplit.length != 2) {
					throw new IllegalStateException(
						"Wrong split: " + strippedLine);					
				}
				final String defenderString = lineSplit[0];
				final String weightString = lineSplit[1];
				final double weight = Double.parseDouble(weightString);
				if (weight <= 0.0 || weight > 1.0) {
					throw new IllegalStateException(
						"Weight is not in [0, 1): " + weight);
				}
				totalWeight += weight;
				
				final String defenderName =
					EncodingUtils.getStrategyName(defenderString);
				final Map<String, Double> defenderParams =
					EncodingUtils.getStrategyParams(defenderString);
				
				final Defender defender =
					AgentFactory.createDefender(
						defenderName, defenderParams, discFact);
				this.defenders.add(defender);
				this.defenderWeights.add(weight);
			}
		}
		final double tol = 0.001;
		if (Math.abs(totalWeight - 1.0) > tol) {
			throw new IllegalStateException(
				"Weights do not sum to 1.0: " + this.defenderWeights);
		}
	}
	
	/**
	 * Return the lines from the given file name in order.
	 * @param fileName the file name to draw from
	 * @return a list of the lines of the file as strings
	 */
	private static List<String> getLines(final String fileName) {
		final List<String> result = new ArrayList<String>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		       result.add(line);
		    }
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * Load the graph and simulation specification, and initialize
	 * the defender opponent and environment.
	 * 
	 * @param simSpecFolderName the folder the simulation spec
	 * file should come from
	 * @param graphFileName the file name of the graph file
	 * @return the discount factor of the environment
	 */
	private double setupEnvironment(
		final String simSpecFolderName,
		final String graphFileName
	) {
		final String graphFolderName = "graphs";
		final GameSimulationSpec simSpec =
			JsonUtils.getSimSpecOrDefaults(simSpecFolderName);	
		// Load graph
		final String filePathName = graphFolderName + File.separator
			+ graphFileName;
		final DependencyGraph depGraph = DGraphUtils.loadGraph(filePathName);
				
		// Load players
		final String defenderString =
			JsonUtils.getDefenderString(simSpecFolderName);
		final String defenderName =
			EncodingUtils.getStrategyName(defenderString);
		final Map<String, Double> defenderParams =
			EncodingUtils.getStrategyParams(defenderString);
		
		final Defender defender =
			AgentFactory.createDefender(
				defenderName, defenderParams, simSpec.getDiscFact());
		final RandomDataGenerator rng = new RandomDataGenerator();
		final int numTimeStep = simSpec.getNumTimeStep();
		final double discFact = simSpec.getDiscFact();
		this.sim = new RLAttackerGameSimulation(
			depGraph, defender,
			rng.getRandomGenerator(), rng,
			numTimeStep, discFact);
		return discFact;
	}
	
	/**
	 * Get a new DepgraphPy4JAttGreedyConfig object for Py4J.
	 * @return the DepgraphPy4JAttGreedyConfig for Py4J to use.
	 */
	public static DepgraphPy4JAttGreedyConfig getGame() {
		return singleton;
	}
	
	/**
	 * Reset the game (clear all actions and reset time steps left).
	 * 
	 * @return the state of the game as a list of doubles
	 */
	public List<Double> reset() {
		// clear the game state.
		this.sim.reset();
		// update the defender at random from the mixed strategy.
		this.sim.setDefender(drawRandomDefender());
		// no nodesToAttack or edgesToAttack so far
		this.nodesToAttack.clear();
		this.edgesToAttack.clear();
		// no action was taken by attacker
		this.attAction = null;
		return getAttObsAsListDouble();
	}
	
	/**
	 * Draw a random defender from defenders, based on the probabilities
	 * in defenderWeights.
	 * @return a randomly drawn defender from defenders
	 */
	private Defender drawRandomDefender() {
		if (this.defenders == null || this.defenders.isEmpty()) {
			throw new IllegalStateException();
		}
		
		final double randDraw = RAND.nextDouble();
		double total = 0.0;
		for (int i = 0; i < this.defenderWeights.size(); i++) {
			total += this.defenderWeights.get(i);
			if (randDraw <= total) {
				return this.defenders.get(i);
			}
		}
		return this.defenders.get(this.defenders.size() - 1);
	}

	/**
	 * Take a step based on the given action, represented as
	 * an integer.
	 * 
	 * Return a flat list representing, in order:
	 * the new attacker observation state,
	 * the reward of the step for player taking action (in R),
	 * whether the game is done (in {0, 1}).
	 * 
	 * Legal actions are 
	 * (count(AND nodes) + count(edges to OR node) + 1) to pass,
	 * or any integer in {1, . . ., count(AND nodes)} that maps to an AND node
	 * not currently in nodesToAttack,
	 * or any integer in 
	 * {count(AND nodes) + 1, . . ., count(AND nodes) + count(edges to OR node)}
	 * that maps to an edge to an OR node not currently in edgesToAttack.
	 * 
	 * If the action is illegal, do not update the game state,
	 * but consider the game as lost (i.e., minimal reward)
	 * and thus done (i.e., 1).
	 * 
	 * If the move is (count(AND nodes) + count(edges to OR node) + 1), 
	 * or if this.nodesToAttack or this.edgesToAttack is not empty and with 
	 * probability this.probGreedySelectionCutOff,
	 * the self agent (attacker) and opponent (defender)
	 * move simultaneously, where the attacker strike this.nodesToAttack
	 * and this.edgesToAttack without adding any more items to them.
	 * 
	 * Otherwise, the agent's selected node ID or edge ID is added to
	 * this.nodesToAttack or this.edgesToAttack
	 * and control returns to the attacker without the defender making a move,
	 * the marginal reward is 0.0, and the time step does not advance.
	 * 
	 * @param action an Integer, the action to take.
	 * The action should be an int in {1, . . .,  
	 * (count(AND nodes) + count(edges to OR node) + 1)}.
	 * The first count(AND nodes) values map to increasing indexes of
	 * AND nodes.
	 * The next count(edges to OR node) values map to increasing
	 * indexes of edges to OR nodes.
	 * The last value maps to the "pass" action.
	 * @return the list representing the new game state,
	 * including the attacker observation, reward, and whether the game is over,
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
		if (this.attAction == null) {
			return new RLAttackerRawObservation(
				this.sim.getLegalToAttackNodeIds(),
				this.sim.getAndNodeIds(),
				this.sim.getNumTimeStep()).toString();
		} else {
			return this.sim.getAttackerObservation(this.attAction).toString();
		}
	}
	
	/**
	 * Observation list is of size [count(AND nodes) + count(edges to OR)] * 2 +
	 *     count(nodes) * ATTACKER_OBS_LENGTH +
	 *     1.
	 * 
	 * First count(AND nodes) items are 1.0 if node is currently
	 * in set to attack, else 0.0.
	 * Next count(edges to OR) items are 1.0 if edge is currently
	 * in set to attack, else 0.0.
	 * 
	 * Next count(AND nodes) items are 1.0 if node is legal to attack,
	 * else 0.0 [all parents ACTIVE, self INACTIVE].
	 * Next count(edges to OR) items are 1.0 if edge is legal to attack,
	 * else 0.0 [source ACTIVE, target INACTIVE].
	 * 
	 * For each of ATTACKER_OBS_LENGTH times,
	 * next count(nodes) items are 1.0 if node was ACTIVE i time steps
	 * ago, else 0.0.
	 * If there are fewer than ATTACKER_OBS_LENGTH previous time steps,
	 * pad with 0's for nonexistent times.
	 * 
	 * Next item is the number of time steps left in the simulation.
	 * 
	 * @return get the attacker observation as a list of Double
	 */
	private List<Double> getAttObsAsListDouble() {
		final List<Double> result = new ArrayList<Double>();
		for (final int nodeId: this.sim.getAndNodeIds()) {
			if (this.nodesToAttack.contains(nodeId)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		for (final int edgeId: this.sim.getEdgeToOrNodeIds()) {
			if (this.edgesToAttack.contains(edgeId)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		
		final List<Integer> legalNodeIds =
			this.sim.getLegalToAttackNodeIds();
		for (final int nodeId: this.sim.getAndNodeIds()) {
			if (legalNodeIds.contains(nodeId)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		final List<Integer> legalEdgeIds =
			this.sim.getLegalToAttackEdgeToOrNodeIds();
		for (final int edgeId: this.sim.getEdgeToOrNodeIds()) {
			if (legalEdgeIds.contains(edgeId)) {
				result.add(1.0);
			} else {
				result.add(0.0);
			}
		}
		
		RLAttackerRawObservation attObs = null;
		if (this.attAction == null) {
			attObs = new RLAttackerRawObservation(
				this.sim.getLegalToAttackNodeIds(),
				this.sim.getAndNodeIds(),
				this.sim.getNumTimeStep());
		} else {
			attObs = this.sim.getAttackerObservation(this.attAction);
		}
		final List<List<Integer>> activeNodeIdsHistory =
			attObs.getActiveNodeIdsHistory();
		for (int t = 0; t < RLAttackerRawObservation.ATTACKER_OBS_LENGTH; t++) {
			final List<Integer> curActiveNodeIds =
				activeNodeIdsHistory.get(activeNodeIdsHistory.size() - 1 - t);
			for (int nodeId = 1; nodeId < this.sim.getNodeCount(); nodeId++) {
				if (curActiveNodeIds.contains(nodeId)) {
					result.add(1.0);
				} else {
					result.add(0.0);
				}
			}
		}
		
		final int timeStepsLeft = attObs.getTimeStepsLeft();
		result.add((double) timeStepsLeft);
		return result;
	}
}
