package moa.classifiers.trees;

import java.util.ArrayList;

import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.AbstractClassifier;
import moa.classifiers.MultiClassClassifier;
import moa.classifiers.core.conditionaltests.NumericAttributeBinaryTest;
import moa.classifiers.trees.HoeffdingTree.Node;
import moa.core.DoubleVector;
import moa.core.Measurement;

public class EmbededHoeffdingTree extends AbstractClassifier implements MultiClassClassifier {

	public ArrayList<HoeffdingTree> arrTrees = new ArrayList<>();
	public ArrayList<Double> arrWeighs = new ArrayList<>();
	public ArrayList<HoeffdingTree> arrTreesAux = new ArrayList<>();
	public ArrayList<Node> arrTreesAuxCurrentNode = new ArrayList<>();
	public ArrayList<HoeffdingTree> arrTreesWeights = new ArrayList<>();
	
	public boolean changed = false;

	public void removeAttFromTrees(int att) {

		arrTreesAux = new ArrayList<>();

		for (HoeffdingTree ht : arrTrees) {
			updateTree(att, ht);
		}

	}

	public void updateTree(int att, HoeffdingTree ht) {

		boolean updated = false;

		StringBuilder sbOriginal = new StringBuilder();
		StringBuilder sbNew = new StringBuilder();
		ht.getDescription(sbOriginal, 1);
		
		recursiveCheckTree(att, ht, null, updated, -1);

		ht.getDescription(sbNew, 1);
		
		if(changed && !arrTreesAux.isEmpty()){
			
			for(int i = 0; i < arrTreesAux.size(); i++){
				arrTrees.add(arrTreesAux.get(i));
			}
			
		}else{
			arrTrees.add(ht);	
		}
		
		

	}

	private void setNodeParent(HoeffdingTree ht, HoeffdingTree.Node currentNode, HoeffdingTree.SplitNode node,
			int idxTreeAux) {

		StringBuilder sbDescriptionCurrentNode = new StringBuilder();
		StringBuilder sbDescriptionComparisonNode = new StringBuilder();
		if(currentNode instanceof HoeffdingTree.SplitNode){
			((HoeffdingTree.SplitNode) currentNode).describeSubtree(ht, sbDescriptionCurrentNode, 0);
			((HoeffdingTree.SplitNode) node).describeSubtree(ht, sbDescriptionComparisonNode, 0);

			if (currentNode instanceof HoeffdingTree.SplitNode) {

				if (sbDescriptionComparisonNode.toString().equals(sbDescriptionCurrentNode.toString())) {
					arrTreesAuxCurrentNode.add(currentNode);
				} else {
					setNodeParent(ht, ((HoeffdingTree.SplitNode) currentNode).getChild(0), node, idxTreeAux);
					setNodeParent(ht, ((HoeffdingTree.SplitNode) currentNode).getChild(1), node, idxTreeAux);
				}
			}	
		}
	}

	private void recursiveCheckTree(int att, HoeffdingTree ht, Node parent, boolean updated, int idxChild) {

		
		// is the root
		if (parent == null) {
			//System.out.println(ht.treeRoot.getClass().getName());
			if (ht.treeRoot instanceof HoeffdingTree.SplitNode) {
				if (((NumericAttributeBinaryTest) ((HoeffdingTree.SplitNode) ht.treeRoot).splitTest).attIndex == att) {

					moa.classifiers.trees.HoeffdingTree h1 = (moa.classifiers.trees.HoeffdingTree) ht.copy();
					moa.classifiers.trees.HoeffdingTree h2 = (moa.classifiers.trees.HoeffdingTree) ht.copy();

					h1.treeRoot = ((moa.classifiers.trees.HoeffdingTree.SplitNode) (h1.treeRoot)).getChild(0);
					h2.treeRoot = ((moa.classifiers.trees.HoeffdingTree.SplitNode) (h2.treeRoot)).getChild(1);

					arrTreesAux.add(h1);
					arrTreesAuxCurrentNode.add(h1.treeRoot);
					recursiveCheckTree(att, arrTreesAux.get(arrTreesAux.size() - 1), null, false, -1);

					arrTreesAux.add(h2);
					arrTreesAuxCurrentNode.add(h2.treeRoot);
					recursiveCheckTree(att, arrTreesAux.get(arrTreesAux.size() - 1), null, false, -1);

					//System.out.println("instanceof works");
					updated = true;
					changed = true;
				} else {
					recursiveCheckTree(att, ht, ht.treeRoot, false, 0);
					recursiveCheckTree(att, ht, ht.treeRoot, false, 1);
				}
			}
		} else {

			
			Node currentNode = ((HoeffdingTree.SplitNode) parent).getChild(idxChild);
			if (currentNode instanceof HoeffdingTree.SplitNode) {
				if (((NumericAttributeBinaryTest) ((HoeffdingTree.SplitNode) currentNode).splitTest).attIndex == att) {

					
					// if both children of current node are split nodes, a new
					// tree will be created with two new branches
					if (((HoeffdingTree.SplitNode) currentNode).getChild(0) instanceof HoeffdingTree.SplitNode
							&& ((HoeffdingTree.SplitNode) currentNode).getChild(1) instanceof HoeffdingTree.SplitNode) {

						//System.out.println("should add 2 trees");
						
//						((HoeffdingTree.SplitNode) parent).setChild(idxChild,
//								((HoeffdingTree.SplitNode) currentNode).getChild(0));
//						recursiveCheckTree(att, ht, parent, true, idxChild);
						arrTreesAux.add((HoeffdingTree) ht.copy());
						setNodeParent(arrTreesAux.get(arrTreesAux.size()-1), arrTreesAux.get(arrTreesAux.size()-1).treeRoot, (HoeffdingTree.SplitNode)parent.copy(), arrTreesAux.size()-1);
						((HoeffdingTree.SplitNode)arrTreesAuxCurrentNode.get(arrTreesAux.size()-1)).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(0));
						
						
						arrTreesAux.add((HoeffdingTree) ht.copy());
						setNodeParent(arrTreesAux.get(arrTreesAux.size()-1), arrTreesAux.get(arrTreesAux.size()-1).treeRoot, (HoeffdingTree.SplitNode)parent.copy(), arrTreesAux.size()-1);
						((HoeffdingTree.SplitNode)arrTreesAuxCurrentNode.get(arrTreesAux.size()-1)).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(1));
						
						recursiveCheckTree(att, arrTreesAux.get(arrTreesAux.size()-2), arrTreesAuxCurrentNode.get(arrTreesAux.size()-2), true, idxChild);
						recursiveCheckTree(att, arrTreesAux.get(arrTreesAux.size()-1), arrTreesAuxCurrentNode.get(arrTreesAux.size()-1), true, idxChild);
						changed = true;
						updated = true;
					}

					// if both children of current node are leaves, a new
					// learning node will be created
					if (((HoeffdingTree.SplitNode) currentNode)
							.getChild(0) instanceof HoeffdingTree.LearningNodeNBAdaptive
							&& ((HoeffdingTree.SplitNode) currentNode)
									.getChild(1) instanceof HoeffdingTree.LearningNodeNBAdaptive) {

						DoubleVector d = new DoubleVector();
						
						//set class distributions for the new learning node
						d.addToValue(0, ((HoeffdingTree.SplitNode) currentNode).getChild(0).observedClassDistribution.getValue(0));
						d.addToValue(0, ((HoeffdingTree.SplitNode) currentNode).getChild(1).observedClassDistribution.getValue(0));
						
						d.addToValue(1, ((HoeffdingTree.SplitNode) currentNode).getChild(0).observedClassDistribution.getValue(1));
						d.addToValue(1, ((HoeffdingTree.SplitNode) currentNode).getChild(1).observedClassDistribution.getValue(1));
						
						if(d.getArrayCopy()[0] > d.getArrayCopy()[1]){
							d = new DoubleVector();
						}
						
						((HoeffdingTree.SplitNode) parent).setChild(idxChild, ht.newLearningNode());
						((HoeffdingTree.SplitNode) parent).getChild(idxChild).observedClassDistribution = d;

						
						//arrTreesAux.add((HoeffdingTree) ht.copy());
						changed = true;
						updated = true;
					}
					

					// if child 0 is leaf and child 1 is split node, a
					// connection (shortcut) will be created
					if (((HoeffdingTree.SplitNode) currentNode)
							.getChild(0) instanceof HoeffdingTree.LearningNodeNBAdaptive
							&& ((HoeffdingTree.SplitNode) currentNode).getChild(1) instanceof HoeffdingTree.SplitNode) {

						//System.out.println("if child 0 is leaf and child 1 is split node, a connection (shortcut) will be created");
						
						((HoeffdingTree.SplitNode) parent).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(1));

						//arrTreesAux.add((HoeffdingTree) ht.copy());
						changed = true;
						updated = true;
					}

					// if child 1 is leaf and child 0 is split node, a
					// connection (shortcut) will be created
					if (((HoeffdingTree.SplitNode) currentNode)
							.getChild(1) instanceof HoeffdingTree.LearningNodeNBAdaptive
							&& ((HoeffdingTree.SplitNode) currentNode).getChild(0) instanceof HoeffdingTree.SplitNode) {

						//System.out.println("if child 1 is leaf and child 0 is split node, a connection (shortcut) will be created");
						
						((HoeffdingTree.SplitNode) parent).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(0));
						//arrTreesAux.add((HoeffdingTree) ht.copy());
						changed = true;
						updated = true;
					}

				} else {
					recursiveCheckTree(att, ht, currentNode, false, 0);
					recursiveCheckTree(att, ht, currentNode, false, 1);
				}

			}
		}

	}
	
	private void recursiveCheckTree2(int att, HoeffdingTree ht, Node parent, boolean updated, int idxChild) {

		
		// is the root
		if (parent == null) {
			//System.out.println(ht.treeRoot.getClass().getName());
			if (ht.treeRoot instanceof HoeffdingTree.SplitNode) {
				if (((NumericAttributeBinaryTest) ((HoeffdingTree.SplitNode) ht.treeRoot).splitTest).attIndex == att) {

					moa.classifiers.trees.HoeffdingTree h1 = (moa.classifiers.trees.HoeffdingTree) ht.copy();
					moa.classifiers.trees.HoeffdingTree h2 = (moa.classifiers.trees.HoeffdingTree) ht.copy();

					h1.treeRoot = ((moa.classifiers.trees.HoeffdingTree.SplitNode) (h1.treeRoot)).getChild(0);
					h2.treeRoot = ((moa.classifiers.trees.HoeffdingTree.SplitNode) (h2.treeRoot)).getChild(1);

					arrTreesAux.add(h1);
					arrTreesAuxCurrentNode.add(h1.treeRoot);
					recursiveCheckTree2(att, arrTreesAux.get(arrTreesAux.size() - 1), null, false, -1);

					arrTreesAux.add(h2);
					arrTreesAuxCurrentNode.add(h2.treeRoot);
					recursiveCheckTree2(att, arrTreesAux.get(arrTreesAux.size() - 1), null, false, -1);

					//System.out.println("instanceof works");
					updated = true;
					changed = true;
				} else {
					recursiveCheckTree2(att, ht, ht.treeRoot, false, 0);
					recursiveCheckTree2(att, ht, ht.treeRoot, false, 1);
				}
			}
		} else {

			
			Node currentNode = ((HoeffdingTree.SplitNode) parent).getChild(idxChild);
			if (currentNode instanceof HoeffdingTree.SplitNode) {
				if (((NumericAttributeBinaryTest) ((HoeffdingTree.SplitNode) currentNode).splitTest).attIndex == att) {

					
					// if both children of current node are split nodes, a new
					// tree will be created with two new branches
					if (((HoeffdingTree.SplitNode) currentNode).getChild(0) instanceof HoeffdingTree.SplitNode
							&& ((HoeffdingTree.SplitNode) currentNode).getChild(1) instanceof HoeffdingTree.SplitNode) {

						//System.out.println("should add 2 trees");
						
//						((HoeffdingTree.SplitNode) parent).setChild(idxChild,
//								((HoeffdingTree.SplitNode) currentNode).getChild(0));
//						recursiveCheckTree(att, ht, parent, true, idxChild);
						arrTreesAux.add((HoeffdingTree) ht.copy());
						setNodeParent(arrTreesAux.get(arrTreesAux.size()-1), arrTreesAux.get(arrTreesAux.size()-1).treeRoot, (HoeffdingTree.SplitNode)parent.copy(), arrTreesAux.size()-1);
						((HoeffdingTree.SplitNode)arrTreesAuxCurrentNode.get(arrTreesAux.size()-1)).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(0));
						
						
						arrTreesAux.add((HoeffdingTree) ht.copy());
						setNodeParent(arrTreesAux.get(arrTreesAux.size()-1), arrTreesAux.get(arrTreesAux.size()-1).treeRoot, (HoeffdingTree.SplitNode)parent.copy(), arrTreesAux.size()-1);
						((HoeffdingTree.SplitNode)arrTreesAuxCurrentNode.get(arrTreesAux.size()-1)).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(1));
						
						recursiveCheckTree(att, arrTreesAux.get(arrTreesAux.size()-2), arrTreesAuxCurrentNode.get(arrTreesAux.size()-2), true, idxChild);
						recursiveCheckTree(att, arrTreesAux.get(arrTreesAux.size()-1), arrTreesAuxCurrentNode.get(arrTreesAux.size()-1), true, idxChild);
						changed = true;
						updated = true;
					}

					// if both children of current node are leaves, a new
					// learning node will be created
					if (((HoeffdingTree.SplitNode) currentNode)
							.getChild(0) instanceof HoeffdingTree.LearningNodeNBAdaptive
							&& ((HoeffdingTree.SplitNode) currentNode)
									.getChild(1) instanceof HoeffdingTree.LearningNodeNBAdaptive) {

						DoubleVector d = new DoubleVector();
						
						
						//@Important: at this point, we suppose that the concept drift took place only in the 
						//majority class, so, we do not set the observations values for the non defective class.
						//set class distributions for the new learning node
//						d.addToValue(0, ((HoeffdingTree.SplitNode) currentNode).getChild(0).observedClassDistribution.getValue(0));
//						d.addToValue(0, ((HoeffdingTree.SplitNode) currentNode).getChild(1).observedClassDistribution.getValue(0));
						
						d.addToValue(1, ((HoeffdingTree.SplitNode) currentNode).getChild(0).observedClassDistribution.getValue(1));
						d.addToValue(1, ((HoeffdingTree.SplitNode) currentNode).getChild(1).observedClassDistribution.getValue(1));
						
						((HoeffdingTree.SplitNode) parent).setChild(idxChild, ht.newLearningNode());
						((HoeffdingTree.SplitNode) parent).getChild(idxChild).observedClassDistribution = d;

						
						//arrTreesAux.add((HoeffdingTree) ht.copy());
						changed = true;
						updated = true;
					}
					

					// if child 0 is leaf and child 1 is split node, a
					// connection (shortcut) will be created
					if (((HoeffdingTree.SplitNode) currentNode)
							.getChild(0) instanceof HoeffdingTree.LearningNodeNBAdaptive
							&& ((HoeffdingTree.SplitNode) currentNode).getChild(1) instanceof HoeffdingTree.SplitNode) {

						//System.out.println("if child 0 is leaf and child 1 is split node, a connection (shortcut) will be created");
						
						((HoeffdingTree.SplitNode) parent).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(1));

						addMinorityObservations(parent, ((HoeffdingTree.SplitNode) currentNode).getChild(0).observedClassDistribution.getValue(1));
						
						//arrTreesAux.add((HoeffdingTree) ht.copy());
						changed = true;
						updated = true;
					}

					// if child 1 is leaf and child 0 is split node, a
					// connection (shortcut) will be created
					if (((HoeffdingTree.SplitNode) currentNode)
							.getChild(1) instanceof HoeffdingTree.LearningNodeNBAdaptive
							&& ((HoeffdingTree.SplitNode) currentNode).getChild(0) instanceof HoeffdingTree.SplitNode) {

						//System.out.println("if child 1 is leaf and child 0 is split node, a connection (shortcut) will be created");
						
						((HoeffdingTree.SplitNode) parent).setChild(idxChild,
								((HoeffdingTree.SplitNode) currentNode).getChild(0));
						
						addMinorityObservations(parent, ((HoeffdingTree.SplitNode) currentNode).getChild(1).observedClassDistribution.getValue(1));
						
						//arrTreesAux.add((HoeffdingTree) ht.copy());
						changed = true;
						updated = true;
					}

				} else {
					recursiveCheckTree(att, ht, currentNode, false, 0);
					recursiveCheckTree(att, ht, currentNode, false, 1);
				}

			}
		}

	}

	
	
	
	private void addMinorityObservations(Node currentNode, double value) {
		
		if(currentNode instanceof HoeffdingTree.SplitNode){
			addMinorityObservations(((HoeffdingTree.SplitNode) currentNode).getChild(0), value);
			addMinorityObservations(((HoeffdingTree.SplitNode) currentNode).getChild(1), value);
		}else{
			if(currentNode instanceof HoeffdingTree.LearningNodeNBAdaptive){
				currentNode.observedClassDistribution.setValue(1, currentNode.observedClassDistribution.getValue(1)+ value);
			}
		}
		
	}

	public void updateClassifiersWeights() {

	}

	@Override
	public boolean isRandomizable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void resetLearningImpl() {
		// TODO Auto-generated method stub

	}

	@Override
	public void trainOnInstanceImpl(Instance inst) {
		// TODO Auto-generated method stub

	}

	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getModelDescription(StringBuilder out, int indent) {
		// TODO Auto-generated method stub

	}

}
