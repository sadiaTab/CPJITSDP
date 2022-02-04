package moa.classifiers.spdisc.meta;

import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import org.joda.time.Days;
import org.joda.time.Instant;

import com.github.javacliparser.IntOption;
import com.github.javacliparser.StringOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.core.Measurement;

public class WFL_ORB extends OO_ORB_Oza{

	
	double[] votes = null;
	
	@Override
	public String getPurposeString() {
		return "Variation of oversampling on-line bagging of Wang et al IJCAI 2016 to use an example for training only once "
				+ "waitingTime days have passed since its creation. This waitingTime is the delay for receiving the label "
				+ "of this example."
				+ "One of the input attributes **after** the class attribute in the examples must be the date of the commit.";
	}

	private static final long serialVersionUID = 1L;

	public IntOption waitingTime = new IntOption("waitingTime", 'w',
			"The time (in days) we have to wait before using a commit as a training example in order to know whether "
					+ "it led to a defect or not.",
			30, 1, Integer.MAX_VALUE);

	public IntOption unixTimeStampIndex = new IntOption("unixTimeStampIndex", 'i',
			"The index of the input attribute containing the unix time stamp when the example was created (starting from 0). "
					+ "It must be an attribute index ***after*** the class attribute index.",
			15, 0, Integer.MAX_VALUE);

	//orb parameters (ws	th	l0	l1	m	n)
	public StringOption paramORB = new StringOption("paramORB", 'p', "set all the parameters for the ORB", "100;0.4;10;12;1.5;3");
	
	// training examples that are waiting for waitingTime days before they are
	// used for training
	// PS: this is probably not the best data structure to be used here
	protected ArrayList<Instance> trainingExamplesQueue = null;

	//limit of examples with the same features so that a buggy commit is considered a noise 
	protected int n;
	
	public WFL_ORB() {
		super();
		
		trainingExamplesQueue = new ArrayList<Instance>(waitingTime.getValue());
	}

	@Override
	public void resetLearningImpl() {
		super.resetLearningImpl();
		trainingExamplesQueue = new ArrayList<Instance>(waitingTime.getValue());
	}

	// This method does not do any training here. It just stores instances for
	// training later on.
	// We only train right before giving a prediction, so that
	// we can update the models with all examples produced at lest waitingTime
	// days ago.
	@Override
	public void trainOnInstanceImpl(Instance inst) {
		
		if(inst.classValue() == 1){
			Instance trainInst = inst.copy();
			int numberRepetitions = 0;
			for (int i = 0; i < pastNonDefectiveInstances.size(); i++) {
				if (isSameInstance(trainInst, pastNonDefectiveInstances.get(i))) {
					numberRepetitions++;
				}
			}
			// if more than n instances are similar, this defective
			// instance is noisy and then discarded
			if (numberRepetitions < n) {
				
				super.trainOnInstanceImpl(trainInst);
				
			}
			
		}else{
			trainingExamplesQueue.add(inst.copy());	
		}
	}

	// Method to allow classes that inherit from this one to call the super
	// method from this class
	public void superTrainOnInstanceImpl(Instance inst) {
		super.trainOnInstanceImpl(inst);
	}

	// Method to allow classes that inherit from this one to call the super
	// method from this class
	public double[] superGetVotesForInstance(Instance inst) {
		return super.getVotesForInstance(inst);
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {

		if(idxTimestamp == -1){
			idxTimestamp = unixTimeStampIndex.getValue();
			setORBParam();
		}
		
		if (firstTimeStamp < 0) {
			firstTimeStamp = (long) inst.value(unixTimeStampIndex.getValue());
		}

		this.currentTimeStamp = (long) inst.value(unixTimeStampIndex.getValue());
		
		//inst.deleteAttributeAt(16);
		
		trainOnInstancesWaitingTime(inst);
		
		// Attribute attTmp = inst.attribute(unixTimeStampIndex.getValue());
		Instance instTmp = inst.copy();
		long instant = (long) instTmp.value(unixTimeStampIndex.getValue());
		instTmp.deleteAttributeAt(unixTimeStampIndex.getValue()); // remove the
																	// time
																	// stamp
																	// attribute
																	// before
																	// predicting
																	// the
																	// instance

		double[] ret = super.getVotesForInstance(instTmp); 
		
		if(votes == null){
			votes = new double[2];
		}else{
			if(ret.length>1){
				if(ret[0] > ret[1]){
					votes[0] = 1;
					votes[1] = 0;	
				}else{
					votes[0] = 0;
					votes[1] = 1;
				}
				
			}else{
				votes[0] = 1;
				votes[1] = 0;
			}
		}
		
		return ret;
	}
	
	public void setORBParam(){
		String orbParam = paramORB.getValue();
		
		//orb parameters (ws	th	l0	l1	m	n)
		StringTokenizer strTok = new StringTokenizer(orbParam, ";");
		this.predictionsWindowSize = new Integer(strTok.nextToken());
		this.th = new Double(strTok.nextToken());
		this.l0 = new Double(strTok.nextToken());
		this.l1 = new Double(strTok.nextToken());
		this.m = new Double(strTok.nextToken());
		this.n = new Integer(strTok.nextToken());
	}

	// inst is the current instance to be predicted. It is used here to
	// determine which examples
	// can already be used for training, based on the waitingTime between the
	// production of those examples
	// and of the new example to be predicted.
	protected void trainOnInstancesWaitingTime(Instance inst) {

		while (trainingExamplesQueue.size() != 0) {

			Instance trainingExampleToPop = trainingExamplesQueue.get(0);
			Instant timeTrainingExampleToPop = new Instant(
					(long) trainingExampleToPop.value(unixTimeStampIndex.getValue()) * 1000);
			
			Instant timeTestingInstance = new Instant(currentTimeStamp * 1000);

			Days daysWaited = Days.daysBetween(timeTrainingExampleToPop, timeTestingInstance);

			if (daysWaited.getDays() >= waitingTime.getValue()) {
				
				// if it is non defective instance, store in the array of non
				// defective reference instances
				pastNonDefectiveInstances.add(trainingExampleToPop.copy());
				super.trainOnInstanceImpl(trainingExampleToPop);
				trainingExamplesQueue.remove(0);
				 
			} else { // if the current training example has less than
						// waitingTime, all examples after it will also have.
						// So, we can return.
				break;
			}

		}
	}

	// receive instances with the unixtimestamp
	private static boolean isSameInstance(Instance ins, Instance ins2) {

		boolean ret = true;

		for (int i = 0; i < ins.numAttributes() - 2; i++) {
			if (ins.value(i) != ins2.value(i)) {
				ret = false;
				break;
			}
		}

		return ret;
	}

	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		Measurement[] measure = super.getModelMeasurementsImpl();
		Measurement[] measurePlus = new Measurement[measure.length + 4];
		for (int i = 0; i < measure.length; ++i) {
			measurePlus[i] = measure[i];
		}

		measurePlus[measure.length] = new Measurement("vote 0", votes[0]);
		measurePlus[measure.length + 1] = new Measurement("vote 1", votes[1]);
		measurePlus[measure.length + 2] = new Measurement("training queue size", trainingExamplesQueue.size());
		measurePlus[measure.length + 3] = new Measurement("time stamp", super.currentTimeStamp);

		return measurePlus;

	}
	
}