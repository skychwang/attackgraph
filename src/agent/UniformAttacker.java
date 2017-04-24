package agent;

import graph.Edge;
import graph.Node;
import graph.INode.NodeActivationType;
import graph.INode.NodeState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import model.AttackCandidate;
import model.AttackerAction;
import model.DependencyGraph;

import org.apache.commons.math3.distribution.AbstractIntegerDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.random.RandomGenerator;

public final class UniformAttacker extends Attacker {
	private int maxNumSelectCandidate;
	private int minNumSelectCandidate;
	private double numSelectCandidateRatio;
	
	public UniformAttacker(final double maxNumSelectCandidate,
		final double minNumSelectCandidate, 
		final double numSelectCandidateRatio) {
		super(AttackerType.UNIFORM);
		if (minNumSelectCandidate < 0 || maxNumSelectCandidate < minNumSelectCandidate
			|| numSelectCandidateRatio < 0.0 || numSelectCandidateRatio > 1.0) {
			throw new IllegalArgumentException();
		}
		this.maxNumSelectCandidate = (int) maxNumSelectCandidate;
		this.minNumSelectCandidate = (int) minNumSelectCandidate;
		this.numSelectCandidateRatio = numSelectCandidateRatio;
	}
	
	/*****************************************************************************************
	 * @param depGraph: dependency graph with current game state
	 * @param curTimeStep: current time step 
	 * @param numTimeStep: total number of time step
	 * @param rng: random generator
	 * @return type of Attacker Action: an attack action
	 *****************************************************************************************/
	@Override
	public AttackerAction sampleAction(
		final DependencyGraph depGraph, 
		final int curTimeStep,
		final int numTimeStep, 
		final RandomGenerator rng) {
		if (depGraph == null || curTimeStep < 0 || numTimeStep < curTimeStep || rng == null) {
			throw new IllegalArgumentException();
		}
		// Select candidate for the attacker
		AttackCandidate attackCandidate = selectCandidate(depGraph); 
		
		// Sample number of nodes
		int totalNumCandidate = attackCandidate.getEdgeCandidateSet().size()
			+ attackCandidate.getNodeCandidateSet().size();
		// Compute number of candidates to select
		int numSelectCandidate = 0;
		if (totalNumCandidate < this.minNumSelectCandidate) {
			numSelectCandidate = totalNumCandidate;
		} else  {
			numSelectCandidate = Math.max(this.minNumSelectCandidate,
				(int) (totalNumCandidate * this.numSelectCandidateRatio));
			numSelectCandidate = Math.min(this.maxNumSelectCandidate, numSelectCandidate);
		}
		// System.out.println(numSelectCandidate + " attacker strikes");
		if (numSelectCandidate == 0) { // if there is no candidate
			return new AttackerAction();
		}
		// Sample nodes
		UniformIntegerDistribution rnd = new UniformIntegerDistribution(rng, 0, totalNumCandidate - 1);
		return sampleAction(depGraph, attackCandidate, numSelectCandidate, rnd);
	}
	
	@Override
	/*****************************************************************************************
	 * @param depGraph: dependency graph with current game state
	 * @param curTimeStep: current time step 
	 * @param numTimeStep: total number of time step
	 * @param rng: random generator
	 * @param numSample: number of actions to sample
	 * @param isReplacement: sampling with replacement or not
	 * @return type of Attacker Action: list of attack actions
	 *****************************************************************************************/
	public List<AttackerAction> sampleAction(
		final DependencyGraph depGraph,
		final int curTimeStep, 
		final int numTimeStep, 
		final RandomGenerator rng,
		final int numSample, 
		final boolean isReplacement) {
		if (depGraph == null || curTimeStep < 0 || numTimeStep < curTimeStep || rng == null
			|| numSample < 1) {
			throw new IllegalArgumentException();
		}
		if (isReplacement) { // this is currently not used, need to check if the isAdded works properly
			Set<AttackerAction> attActionSet = new HashSet<AttackerAction>();
			int i = 0;
			while (i < numSample) {
				AttackerAction attAction = sampleAction(depGraph, curTimeStep, numTimeStep, rng);
				boolean isAdded = attActionSet.add(attAction);
				if (isAdded) {
					i++;
				}
			}
			return new ArrayList<AttackerAction>(attActionSet);
		}
		 // this is currently used, correct
		List<AttackerAction> attActionList = new ArrayList<AttackerAction>();
		for (int i = 0; i < numSample; i++) {
			AttackerAction attAction = sampleAction(depGraph, curTimeStep, numTimeStep, rng);
			attActionList.add(attAction);
		}
		return attActionList;
	}
	
	/*****************************************************************************************
	* @param depGraph dependency graph
	* @param attackCandidate candidate set
	* @param numSelectCandidate number of candidates to select
	* @param rnd integer distribution randomizer
	* @return type of AttackerAction: an action for the attacker
	*****************************************************************************************/
	private static AttackerAction sampleAction(
		final DependencyGraph depGraph,
		final AttackCandidate attackCandidate, 
		final int numSelectCandidate, 
		final AbstractIntegerDistribution rnd) {
		if (depGraph == null || numSelectCandidate < 0 || rnd == null || attackCandidate == null) {
			throw new IllegalArgumentException();
		}
		AttackerAction action = new AttackerAction();
		List<Edge> edgeCandidateList = new ArrayList<Edge>(attackCandidate.getEdgeCandidateSet());
		List<Node> nodeCandidateList = new ArrayList<Node>(attackCandidate.getNodeCandidateSet());
		int totalNumCandidate = edgeCandidateList.size() + nodeCandidateList.size();

		boolean[] isChosen = new boolean[totalNumCandidate]; // check if this candidate is already chosen
		for (int i = 0; i < totalNumCandidate; i++) {
			isChosen[i] = false;
		}
		int count = 0;
		while (count < numSelectCandidate) {
			int idx = rnd.sample(); // randomly chooses a candidate
			if (!isChosen[idx]) { // if this candidate is not chosen
				if (idx < edgeCandidateList.size()) { // select edge
					Edge selectEdge = edgeCandidateList.get(idx);
					action.addOrNodeAttack(selectEdge.gettarget(), selectEdge);
				} else { // select node, this is for AND node only
					Node selectNode = nodeCandidateList.get(idx - edgeCandidateList.size());
					action.addAndNodeAttack(selectNode, depGraph.incomingEdgesOf(selectNode));
				}
				isChosen[idx] = true; // set chosen to be true
				count++;
			}	
		}
		return action;
	}
	
	/*****************************************************************************************
	* @param depGraph dependency graph with current game state
	* @return type of AttackCandidate: candidate set for the attacker
	*****************************************************************************************/
	private static AttackCandidate selectCandidate(final DependencyGraph depGraph) {
		if (depGraph == null) {
			throw new IllegalArgumentException();
		}
		AttackCandidate aCandidate = new AttackCandidate();
		
		// Check if all targets are already active, then the attacker doesn't need to do anything
		boolean isAllTargetActive = true;
		for (Node target : depGraph.getTargetSet()) {
			if (target.getState() != NodeState.ACTIVE) {
				isAllTargetActive = false;
				break;
			}
		}
		// Start selecting candidate when some targets are inactive
		if (!isAllTargetActive) {
			for (Node node : depGraph.vertexSet()) {
				if (node.getState() == NodeState.INACTIVE) { // only check inactive nodes
					boolean isCandidate = false;
					if (node.getActivationType() == NodeActivationType.AND) { // if this node is AND type
						isCandidate = true;
						for (Edge inEdge : depGraph.incomingEdgesOf(node)) {
							if (inEdge.getsource().getState() == NodeState.INACTIVE) {
								isCandidate = false;
								break;
							}
						}
					} else { // if this node is OR type
						for (Edge inEdge : depGraph.incomingEdgesOf(node)) {
							if (inEdge.getsource().getState() != NodeState.INACTIVE) {
								isCandidate = true;
								break;
							}
						}
					}
					
					if (isCandidate) { // if this node is a candidate
						// if AND node, then add node to the candidate set
						if (node.getActivationType() == NodeActivationType.AND) {
							aCandidate.addNodeCandidate(node);
						} else { // if OR node, then add edges to the candidate set
							for (Edge inEdge : depGraph.incomingEdgesOf(node)) {
								if (inEdge.getsource().getState() == NodeState.ACTIVE) {
									aCandidate.addEdgeCandidate(inEdge);
								}
							}
						}
					}
				}
			}
		}
		return aCandidate;
	}

	public int getMaxNumSelectCandidate() {
		return this.maxNumSelectCandidate;
	}

	public int getMinNumSelectCandidate() {
		return this.minNumSelectCandidate;
	}

	public double getNumSelectCandidateRatio() {
		return this.numSelectCandidateRatio;
	}

	@Override
	public String toString() {
		return "UniformAttacker [maxNumSelectCandidate=" + this.maxNumSelectCandidate
			+ ", minNumSelectCandidate=" + this.minNumSelectCandidate
			+ ", numSelectCandidateRatio=" + this.numSelectCandidateRatio + "]";
	}
}
