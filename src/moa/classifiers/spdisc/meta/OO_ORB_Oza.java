
/**
 * Author: George G. Cabral (george.gcabral@ufrpe.br)
 * Implementation of an Oversampling Online methodology able adapt the oversampling rate 
 * based on the performance of the model in a certain time in the past. Thus, increasing the oversampling
 * rate of the class with poorer performance.
 * 
 * Class Imbalance Evolution and Verification Latency in Just-in-Time Software Defect Prediction, ICSE'19. 
 * 
 * This implements the algorithm proposed in the above paper. So, it should reflect those results.
 * 
 */

package moa.classifiers.spdisc.meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import org.joda.time.Days;
import org.joda.time.Instant;
import org.joda.time.ReadableInstant;

import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.StringOption;
import com.yahoo.labs.samoa.instances.Instance;

import moa.classifiers.Classifier;
import moa.classifiers.meta.OzaBag;
import moa.core.DoubleVector;
import moa.core.Measurement;
import moa.core.MiscUtils;

public class OO_ORB_Oza extends OzaBag{

	
	//orb parameters (ws	th	l0	l1	m	n)
	public int predictionsWindowSize;
	public double th;
	public double l0;
	public double l1;
	public double m;
	
	
	//the index of the time stamp. Assigned in the child class (WFL_OO_ORB)
	public int idxTimestamp = -1;
	
	//pool containing anticipated defective instances that happened before 90 days and 
	//made the problem imbalanced towards the defective class. 
	//The idea is to release these examples slowly to make the problem less imbalanced towards
	//the defective class in the begining of the  string
	public ArrayList<Instance> poolInitialDefectiveInstances = new ArrayList<>();

	// this array stores the non defective instances that are used to be
	// compared to each defective instance during the training
	// time in order to check whether the defective instfance is noisy or not.
	public ArrayList<Instance> pastNonDefectiveInstances = new ArrayList<>();

	// array for early stoping the obf adjustment
	public ArrayList<Integer> pastPredictions = new ArrayList<>();

	// last n instances that comprise the instances seen at the moment of each
	// model in the poolModels
	public HashMap<Integer, Vector<Instance>> poolLastInstances = new HashMap<>();

	public Vector<Classifier[]> poolModels = new Vector();
	
	public int ctInstancesSeen = 0;

	// time stamp of the first day of the stream
	protected long firstTimeStamp = -100;

	public long currentTimeStamp = 0l;

	private static final long serialVersionUID = 1L;

	public FloatOption theta = new FloatOption("theta", 't', "The time decay factor for class size.", 0.9, 0, 1);

	public StringOption classifierOption = new StringOption("classifierOption", 'c',
			"Specific options for the used classifier.", "-m OzaBag -s 20");

	public IntOption storedDaysRetraining = new IntOption("storedDaysRetraining", 'r', "The number of days stored in a pool of instances for retraining to recovery from overfitting the minority class.", 5, 1, Integer.MAX_VALUE);
	
	protected double classSize[]; // time-decayed size of each class

	@Override
	public String getPurposeString() {
		return "Oversampling on-line Oversampling Rate Boosting Cabral et. al. ICSE'19.";
	}

	public OO_ORB_Oza() {
		super();
		classSize = null;
	}

	@Override
	public void trainOnInstanceImpl(Instance inst) {
		

		if(inst.classValue() == 1){
			poolInitialDefectiveInstances.add(inst.copy());
		}
		
		if(inst.classValue() == 0){
			trainModel(inst);
			if(!poolInitialDefectiveInstances.isEmpty()){
				trainModel(poolInitialDefectiveInstances.get(0));
				poolInitialDefectiveInstances.remove(0);
			}
		}

	}
	
	
	
	public void trainModel(Instance inst) {
		//System.out.print(theta.getValue());
		
		this.updatePoolLastInstances(inst.copy());
		
		updateClassSize(inst);
		
		double obf = getOBFPredAvg();

		inst.deleteAttributeAt(idxTimestamp); // remove the time
															// stamp before
															// using the
		
		for (int i = 0; i < this.ensemble.length; i++) {

			double lambda = calculatePoissonLambda(inst);
			int k = MiscUtils.poisson(lambda, this.classifierRandom);

			if (inst.classValue() == 1 && obf > 0) {
				k *= obf;				
			}

			if (inst.classValue() == 0 && obf < -1) {
				k *= -obf;
			}
			
			if (k > 0) {
				Instance weightedInst = (Instance) inst.copy();
				weightedInst.setWeight(inst.weight() * k);
				this.ensemble[i].trainOnInstance(weightedInst);

			}
		}

	}
	
	
	public double getPredAvg() {
		Double average = pastPredictions.stream().mapToInt(val -> val).average().orElse(0.0);
		
		return average;
	}
	
	
	public double getOBFPredAvg() {

		double obf = 1;

		Double average = getPredAvg();
		double threshold = th;
		double y = m;
		
		//boost class 1
		if (average < threshold) {
			average = Math.abs(average - threshold);
			obf = ((Math.pow(y,(average*10))-1)/(Math.pow(y,threshold*10) - 1)*l1)+1;
			
		//boost class 0	
		} else {
			obf = ((Math.pow(y,(average*10))-Math.pow(y,(threshold*10)))/(Math.pow(y,(10)) - Math.pow(y,(threshold * 10)))*l0)+1;
			obf = obf * -1;
		}

		if(Math.abs(obf) < 1){
			obf = 1;
		}
		
		return obf;
	}

	@Override
	public double[] getVotesForInstance(Instance inst) {

		double[] combinedVote = super.getVotesForInstance(inst);

		if (combinedVote.length == 2) {
			if (combinedVote[0] > combinedVote[1]) {
				pastPredictions.add(0);
			} else {
				pastPredictions.add(1);
			}

			if (pastPredictions.size() > predictionsWindowSize) {
				pastPredictions.remove(0);
			}
		}

		if (this.getPredAvg() >= 0.8) {
            Classifier[] classifier = this.poolModels.firstElement();
            for (Vector<Instance> v : this.poolLastInstances.values()) {
                for (Instance in : v) {
                    this.trainOnOldInstance(in.copy(), classifier);
                }
            }
            this.ensemble = (Classifier[])classifier.clone();
        }
		
		ctInstancesSeen++;

		return combinedVote;
	}
	
	public void trainOnOldInstance(Instance inst, Classifier[] oldModel) {
        double lambda = this.calculatePoissonLambda(inst);
        inst.deleteAttributeAt(idxTimestamp);
        for (int i = 0; i < oldModel.length; ++i) {
            int k = MiscUtils.poisson((double)lambda, (Random)this.classifierRandom);
            if (inst.classValue() == 0.0) {
                k = MiscUtils.poisson((double)1.0, (Random)this.classifierRandom);
            }
            if (k <= 0) continue;
            Instance weightedInst = inst.copy();
            weightedInst.setWeight(inst.weight() * (double)k);
            oldModel[i].trainOnInstance(weightedInst);
        }
    }

	private void updatePoolLastInstances(Instance inst) {
        Instant timeFirstExampleStream = new Instant(this.firstTimeStamp * 1000L);
        
        Instance instCopy = inst.copy();
        
        Instant timeTestingExample = new Instant((long)instCopy.value(idxTimestamp) * 1000L);
        
        Days daysPassedFromBegin = Days.daysBetween((ReadableInstant)timeFirstExampleStream, (ReadableInstant)timeTestingExample);
        
        if (!this.poolLastInstances.containsKey(daysPassedFromBegin.getDays())) {
            Vector<Instance> vet = new Vector<Instance>();
        
            vet.add(instCopy);
            
            this.poolLastInstances.put(daysPassedFromBegin.getDays(), vet);
            
            this.poolModels.add((Classifier[])this.ensemble.clone());
            
            if (this.poolLastInstances.size() > this.storedDaysRetraining.getValue()) {
                int olderSetInstances = 1000000;
                Iterator<Integer> iterator = this.poolLastInstances.keySet().iterator();
                while (iterator.hasNext()) {
                    int i = iterator.next();
                    if (i >= olderSetInstances) continue;
                    olderSetInstances = i;
                }
                this.poolLastInstances.remove(olderSetInstances);
                this.poolModels.remove(0);
            }
        } else {
            this.poolLastInstances.get(daysPassedFromBegin.getDays()).add(instCopy);
        }
    }

	
	protected void updateClassSize(Instance inst) {
		if (this.classSize == null) {
			classSize = new double[inst.numClasses()];

			for (int i = 0; i < classSize.length; ++i) {
				classSize[i] = 1d / classSize.length;
			}
		}

		for (int i = 0; i < classSize.length; ++i) {
			classSize[i] = theta.getValue() * classSize[i]
					+ (1d - theta.getValue()) * ((int) inst.classValue() == i ? 1d : 0d);
		}
	}

	// classInstance is the class corresponding to the instance for which we
	// want to calculate lambda
	// will result in an error if classSize is not initialised yet
	// OVERSAMPLING
	public double calculatePoissonLambda(Instance inst) {
		double lambda = 1d;
		int majClass = getMajorityClass();

		lambda = classSize[majClass] / classSize[(int) inst.classValue()];

		return lambda;
	}

	// will result in an error if classSize is not initialised yet
	public int getMajorityClass() {
		int indexMaj = 0;

		for (int i = 1; i < classSize.length; ++i) {
			if (classSize[i] > classSize[indexMaj]) {
				indexMaj = i;
			}
		}
		return indexMaj;
	}

	// will result in an error if classSize is not initialised yet
	public int getMinorityClass() {
		int indexMin = 0;

		for (int i = 1; i < classSize.length; ++i) {
			if (classSize[i] <= classSize[indexMin]) {
				indexMin = i;
			}
		}
		return indexMin;
	}

	// will result in an error if classSize is not initialised yet
	@Override
	protected Measurement[] getModelMeasurementsImpl() {
		Measurement[] measure = super.getModelMeasurementsImpl();
		Measurement[] measurePlus = null;

		if (classSize != null) {
			measurePlus = new Measurement[measure.length + classSize.length];

			for (int i = 0; i < measure.length; ++i) {
				measurePlus[i] = measure[i];
			}

			for (int i = 0; i < classSize.length; ++i) {
				String str = "size of class " + i;
				measurePlus[measure.length + i] = new Measurement(str, classSize[i]);
			}

			

		} else {
			measurePlus = new Measurement[measure.length + 2];
			for (int i = 0; i < measure.length; ++i) {
				measurePlus[i] = measure[i];
			}

			for (int i = 0; i < 2; ++i) {
				String str = "size of class " + i;
				measurePlus[measure.length + i] = new Measurement(str, 0);
			}

			
		}
		// measurePlus = measure;

		return measurePlus;
	}

	@Override
	public boolean isRandomizable() {
		return true;
	}

}

