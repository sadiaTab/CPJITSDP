
// See comments marked with <---le15Aug2017 in WaitForLabelsOzaBag for information about deleting attributes from instances. 

// See <---le19/01/18 in OOB.java for modifications done to start class size as equal for all classes.
// These modifications have been made after the preliminary experiments for the technical report related to the first grant proposal.

// See <---le19/01/18 in AbstractClassifier, for making randomSeedOption public, so that one can select the option in the GUI.
// These modifications have been made after the preliminary experiments for the technical report related to the first grant proposal.

/**
 * 
 * ***** Needs to specify the full path to the joda jar in the classpath if you want to run from the command line:
 * /Users/llm11/Leandro\'s\ Files/Work/Approaches/MOA-2016.04-leandro/lib/joda-time-2.4.jar
 * 
 * I believe this is the class used for the experiments in the technical report for my EPSRC First Grant proposal.
 * 
 * Author: Leandro L. Minku (leandro.minku@leicester.ac.uk)
 * Implementation of a variation of OOB that only uses a training example for training once 
 * waitingTime days have passed between the creation of the corresponding commit and its use for training.
 * 
 * This is needed in commit defect prediction because we need to wait for a while to see if a defect will be
 * associated to a commit or not before using this commit for training, i.e., the label is assumed here to
 * take at most waitingTime days to arrive.
 * 
 * This is basically a copy of the class meta.WaitForLabelsOzaBag, but inheriting from OOB
 * instead of from OzaBag.
 * 
 */


package moa.classifiers.meta.ggc2.meta;

import java.util.ArrayList;

import org.joda.time.Days;
import org.joda.time.Instant;

import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.meta.OOB;
import moa.core.Measurement;

public class WaitForLabelsOOB extends OOB {

	@Override
	public String getPurposeString() {
		return "Variation of oversampling on-line bagging of Wang et al IJCAI 2016 to use an example for training only once " +
				"waitingTime days have passed since its creation. This waitingTime is the delay for receiving the label " +
				"of this example." + 
				"One of the input attributes **after** the class attribute in the examples must be the date of the commit.";
	}

	private static final long serialVersionUID = 1L;
	
	
	
//waiting period changing to 30 or 90
	public IntOption waitingTime = new IntOption("waitingTime", 'w',
			"The time (in days) we have to wait before using a commit as a training example in order to know whether "+
					"it led to a defect or not.", 90, 1, Integer.MAX_VALUE);

	public IntOption unixTimeStampIndex = new IntOption("unixTimeStampIndex", 'i',
			"The index of the input attribute containing the unix time stamp when the example was created (starting from 0). " +
					"It must be an attribute index ***after*** the class attribute index.", 15, 0, Integer.MAX_VALUE);

	// training examples that are waiting for waitingTime days before they are used for training
	// PS: this is probably not the best data structure to be used here
	protected ArrayList<Instance> trainingExamplesQueue = null;
	public double currentClass = 0;


	public WaitForLabelsOOB() {
		super();
		trainingExamplesQueue = new ArrayList<Instance>(waitingTime.getValue());
		
		
	}

	@Override
	public void resetLearningImpl() {
		super.resetLearningImpl();
		trainingExamplesQueue = new ArrayList<Instance>(waitingTime.getValue());
	}

	// This method does not do any training here. It just stores instances for training later on. 
	// We only train right before giving a prediction, so that 
	// we can update the models with all examples produced at lest waitingTime days ago.
	@Override
	public void trainOnInstanceImpl(Instance inst) {
		//System.out.println("Waiting "+waitingTime.getValue());
		if(inst.classValue() == 1){
			Instance trainInst = inst.copy();
			int numberRepetitions = 0;
			for (int i = 0; i < pastNonDefectiveInstances.size(); i++) {
				if (isSameInstance(trainInst, pastNonDefectiveInstances.get(i))) {
					numberRepetitions++;
				}
			}
			// if more than 3 instances are similar, this defective
			// instance is noisy and then discarded
			if (numberRepetitions < 3) {
				trainInst.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the time stamp before using the example for training
				super.trainOnInstanceImpl(trainInst);
			}
			
		}else{
			trainingExamplesQueue.add(inst.copy());	
		} 
	}
	
	// Method to allow classes that inherit from this one to call the super method from this class
	public void superTrainOnInstanceImpl(Instance inst) {
		super.trainOnInstanceImpl(inst);
	}
	// Method to allow classes that inherit from this one to call the super method from this class
	public double[] superGetVotesForInstance(Instance inst) {
		return super.getVotesForInstance(inst);
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {
		trainOnInstancesWaitingTime(inst);
		//Attribute attTmp = inst.attribute(unixTimeStampIndex.getValue());
		Instance instTmp = inst.copy();
		this.currentTimeStamp = (long) instTmp.copy().value(unixTimeStampIndex.getValue());
		instTmp.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the time stamp attribute before predicting the instance
		
		
		
		currentClass = 0;
		currentClass = instTmp.classValue();
		
		
		return super.getVotesForInstance(instTmp);
	}

	// inst is the current instance to be predicted. It is used here to determine which examples
	// can already be used for training, based on the waitingTime between the production of those examples
	// and of the new example to be predicted.
	protected void trainOnInstancesWaitingTime(Instance inst) {

		// unix timestamp is in seconds, whereas Instant receives miliseconds from from 1970-01-01T00:00:00Z.
		Instant timeTestingInstance = new Instant((long)inst.value(unixTimeStampIndex.getValue())*1000);

		while (trainingExamplesQueue.size()!=0) {

			Instance trainingExampleToPop = trainingExamplesQueue.get(0);
			Instant timeTrainingExampleToPop = new Instant((long)trainingExampleToPop.value(unixTimeStampIndex.getValue())*1000);
			Days daysWaited = Days.daysBetween(timeTrainingExampleToPop,timeTestingInstance);

			if (daysWaited.getDays() >= waitingTime.getValue()) {
				
				
				//if it is non defective instance, store in the array of non defective reference instances
				if(trainingExampleToPop.classValue() == 0){
					trainingExampleToPop.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the time stamp before using the example for training
					pastNonDefectiveInstances.add(trainingExampleToPop.copy());
					super.trainOnInstanceImpl(trainingExampleToPop);
					trainingExamplesQueue.remove(0);
				}else{
					//check how many non defective instances are similar to the current defective instance
					int numberRepetitions = 0;
					for(int i = 0; i < pastNonDefectiveInstances.size(); i++){
						if(isSameInstance(trainingExampleToPop, pastNonDefectiveInstances.get(i))){
							numberRepetitions++;
						}
					}
					//if more than 3 instances are similar, this defective instance is noisy and then discarded
					if(numberRepetitions < 3){
						trainingExampleToPop.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the time stamp before using the example for training
						super.trainOnInstanceImpl(trainingExampleToPop);
						trainingExamplesQueue.remove(0);	
					}
				}
				
				
			} else { // if the current training example has less than waitingTime, all examples after it will also have. So, we can return.
				break;
			}

		}
	}
	
	//receive instances without the unixtimestamp
	private static boolean isSameInstance(Instance ins, Instance ins2) {

		boolean ret = true;

		for (int i = 0; i < ins.numAttributes() - 1; i++) {
			if (ins.value(i) != ins2.value(i)) {
				ret = false;
				break;
			}
		}

		return ret;
	}

	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		Measurement [] measure = super.getModelMeasurementsImpl();
		Measurement[] measurePlus = new Measurement[measure.length + 3];
		for (int i = 0; i < measure.length; ++i) {
			measurePlus[i] = measure[i];
		}

		measurePlus[measure.length] = new Measurement("training queue size", trainingExamplesQueue.size());
		measurePlus[measure.length + 1] = new Measurement("time stamp", super.currentTimeStamp);
		measurePlus[measure.length + 2] = new Measurement("class", currentClass);

		return measurePlus;

	}

}
