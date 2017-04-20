package agent;

import java.util.ArrayList;
import java.util.List;

import graph.Node;
import model.DefenderAction;
import model.DefenderBelief;
import model.DefenderObservation;
import model.DependencyGraph;

import org.apache.commons.math3.distribution.AbstractIntegerDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.random.RandomGenerator;

public final class UniformDefender extends Defender {
	private int maxNumRes;
	private int minNumRes;
	private double numResRatio;
	
	public UniformDefender(final double maxNumRes, final double minNumRes, final double numResRatio) {
		super(DefenderType.UNIFORM);
		if (maxNumRes < 1 || minNumRes > maxNumRes || numResRatio < 0.0 || numResRatio > 1.0) {
			throw new IllegalArgumentException();
		}
		this.maxNumRes = (int) maxNumRes;
		this.minNumRes = (int) minNumRes;
		this.numResRatio = numResRatio;
	}

	@Override
	public DefenderAction sampleAction(final DependencyGraph depGraph,
		final int curTimeStep, final int numTimeStep, final DefenderBelief dBelief, final RandomGenerator rng) {
		if (depGraph == null || curTimeStep < 0 || numTimeStep < curTimeStep || dBelief == null || rng == null) {
			throw new IllegalArgumentException();
		}
		List<Node> dCandidateNodeList = new ArrayList<Node>(depGraph.vertexSet());
		int numNodetoProtect = 0;
		if (dCandidateNodeList.size() < this.minNumRes) {
			numNodetoProtect = dCandidateNodeList.size();
		} else {
			numNodetoProtect = Math.max(this.minNumRes, (int) (this.numResRatio * dCandidateNodeList.size()));
			numNodetoProtect = Math.min(this.maxNumRes, numNodetoProtect);
		}
		if (dCandidateNodeList.size() == 0) {
			return new DefenderAction();
		}
		// Sample nodes
		UniformIntegerDistribution rnd = new UniformIntegerDistribution(rng, 0, dCandidateNodeList.size() - 1);
		return sampleAction(dCandidateNodeList, numNodetoProtect, rnd);	
	}

	@Override
	public DefenderBelief updateBelief(final DependencyGraph depGraph,
		final DefenderBelief currentBelief, final DefenderAction dAction,
		final DefenderObservation dObservation, final int curTimeStep, final int numTimeStep,
		final RandomGenerator rng) {
		return new DefenderBelief();
	}
	
	private static DefenderAction sampleAction(final List<Node> dCandidateNodeList,
		final int numNodetoProtect,
		final AbstractIntegerDistribution rnd) {
		if (dCandidateNodeList == null || numNodetoProtect < 0 || rnd == null) {
			throw new IllegalArgumentException();
		}
		DefenderAction action = new DefenderAction();
		
		boolean[] isChosen = new boolean[dCandidateNodeList.size()];
		for (int i = 0; i < dCandidateNodeList.size(); i++) {
			isChosen[i] = false;
		}
		int count = 0;
		while (count < numNodetoProtect) {
			int idx = rnd.sample();
			if (!isChosen[idx]) {
				action.addNodetoProtect(dCandidateNodeList.get(idx));
				isChosen[idx] = true;
				count++;
			}
				
		}
		return action;
	}
}
