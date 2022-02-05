/*
 *    CpjitsdpFiltering.java
 *    
 *    Copyright (C) 2022 University of Birmingham, Birmingham, United Kingdom
 *    @author Sadia Tabassum (sxt901@student.bham.ac.uk)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *     
 *    
 */

package moa.tasks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.StringTokenizer;
import java.util.Vector;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;

import cpjitsdpexperiment.ExpFilter;
import moa.classifiers.MultiClassClassifier;
import moa.core.Example;
import moa.core.ObjectRepository;
import moa.core.TimingUtils;
import moa.core.Utils;
import moa.evaluation.EWMAClassificationPerformanceEvaluator;
import moa.evaluation.FadingFactorClassificationPerformanceEvaluator;
import moa.evaluation.LearningCurve;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.WindowClassificationPerformanceEvaluator;
import moa.learners.Learner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.tasks.ClassificationMainTask;
import moa.tasks.TaskMonitor;


//ORB parameters: 100;0.4;10;12;1.5;3
//Filter parameters: 500;50;0.7;500

public class CpjitsdpFiltering extends ClassificationMainTask {

	private static HashMap<String, Object> hashResults = new HashMap<>();
	private static final String RES_INSTANCE_CLASS = "instanceClass";
	private static final String RES_INSTANCE_PREDICTION = "instancePrediction";
	private static final String RES_RECALL_0 = "recall_0";
	private static final String RES_RECALL_1 = "recall_1";
	private static final String RES_TS = "timestamp";
	private static final String RES_TELAPSD = "timeelapsed";

	public ArrayList<Example> CPQ = new ArrayList<>(); //Queue for old cp instances
	public List<Example> window  = new ArrayList<>(windowSize);	//window for keeping most recent wp instances.

	public static int windowSize = 0;
	public static int numOfshortD=0;
	public static double maxAllowedDistance=0;
	public static int cpqSize=0;

	public boolean trainWithInst = false;
	public double maxAvg=0;
	public int trw=0;
	public int suitableCP=0;
	public int y=0;

	@Override
	public String getPurposeString() {
		return "Evaluates a classifier on a stream by testing then training with each example in sequence and respecting the "
				+ "waiting period for finding the true labels.";
	}

	// Code representing each commit label scenario.
	// the commit is not buggy
	private static final int NOT_BUG = 0;
	// the commit is buggy but its true label was not found within W days for test dataset
	private static final int BUG_NOT_DISCOVERED_W_DAYS_TEST = 1;
	// the commit is buggy and its true label was found within W days for test dataset
	private static final int BUG_DISCOVERED_W_DAYS_TEST = 2;
	// the true label of a defective commit was assigned.
	private static final int BUG_FOUND = 3;
	// the commit is buggy but its true label was not found within W days for nontest dataset
	private static final int BUG_NOT_DISCOVERED_W_DAYS_NOT_TEST = 4;
	// the commit is buggy and its true label was found within W days for nontest dataset
	//	private static final int BUG_DISCOVERED_W_DAYS_NOT_TEST = 5;

	private static final long serialVersionUID = 1L;

	public ClassOption learnerOption = new ClassOption("learner", 'l', "Learner to train.", MultiClassClassifier.class,
			"moa.classifiers.bayes.NaiveBayes");

	public ClassOption streamOption = new ClassOption("stream", 's', "Stream to learn from.", ExampleStream.class,
			"generators.RandomTreeGenerator");

	public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
			"Classification performance evaluation method.", LearningPerformanceEvaluator.class,
			"WindowClassificationPerformanceEvaluator");

	public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
			"Maximum number of instances to test/train on  (-1 = no limit).", 100000000, -1, Integer.MAX_VALUE);

	public IntOption timeLimitOption = new IntOption("timeLimit", 't',
			"Maximum number of seconds to test/train for (-1 = no limit).", -1, -1, Integer.MAX_VALUE);

	public IntOption sampleFrequencyOption = new IntOption("sampleFrequency", 'f',
			"How many instances between samples of the learning performance.", 100000, 0, Integer.MAX_VALUE);

	public IntOption memCheckFrequencyOption = new IntOption("memCheckFrequency", 'q',
			"How many instances between memory bound checks.", 100000, 0, Integer.MAX_VALUE);

	public FileOption dumpFileOption = new FileOption("dumpFile", 'd', "File to append intermediate csv results to.",
			null, "csv", true);

	public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
			"File to append output predictions to.", null, "pred", true);


	public IntOption widthOption = new IntOption("width", 'w', "Size of Window", 1000);

	public FloatOption alphaOption = new FloatOption("alpha", 'a', "Fading factor or exponential smoothing factor",
			.01);

	public void setFilterParam(String paramFilter){
		String filterParam = paramFilter;
		StringTokenizer strTok = new StringTokenizer(filterParam, ";");
		this.windowSize = new Integer(strTok.nextToken());
		this.numOfshortD = new Integer(strTok.nextToken());
		this.maxAllowedDistance = new Double(strTok.nextToken());
		this.cpqSize = new Integer(strTok.nextToken());

		System.out.println(windowSize+";"+numOfshortD+";"+maxAllowedDistance+";"+cpqSize);
	}

	@Override
	public Class<?> getTaskResultType() {
		return LearningCurve.class;
	}

	public boolean filterInst(Example trainInst,ArrayList<Example> window, int project_no, int pNo) {
		Example tr=trainInst.copy();
		double avgDistFil = 0;
		if(project_no==pNo) {
			trainWithInst=true;
			if(window.size()==windowSize) {
				window.remove(0);
			}
			window.add(tr);
			y++;
		}
		else{

			if(window.size()==0) {
				trainWithInst=true;
			}
			else{
				avgDistFil = getAvgDist(tr,window);
				if(avgDistFil<=(maxAllowedDistance*maxAllowedDistance)) {
					trainWithInst=true;
					suitableCP++;
				}
				else {
					trainWithInst=false;
				}
			}
		}
		return trainWithInst;
	}

	public double getAvgDist(Example trainInst, ArrayList<Example> window) {
		int labelTrainInst = (int) ((Instance) trainInst.getData()).value(15);
		double attr=0;
		List<Double> distArr  = new ArrayList<>();
		Iterator it = window.iterator();
		while(it.hasNext()) {
			double distTrainandWindow=0;
			Example windowInst = (Example) it.next();
			int windowInstLabel = (int) ((Instance) trainInst.getData()).value(15);
			if(labelTrainInst == windowInstLabel) {

				for(int i=0; i<15; i++) {
					attr = Math.abs((double) ((Instance) trainInst.getData()).value(i) - (double) ((Instance) windowInst.getData()).value(i));
					distTrainandWindow += attr*attr;
				}

				distArr.add(distTrainandWindow);
			}
		}
		Collections.sort(distArr);
		double sumDist = 0;
		double avgDist = 0;
		double avgDistNormal = 0;
		int p = 0;
		if(distArr.size()<numOfshortD) {
			p=distArr.size();
		}
		else {
			p=numOfshortD;
		}
		List<Double> shorterDist = distArr.subList(0,p);
		for(Double d : shorterDist) {
			sumDist += d;
		}
		avgDist = sumDist/p;

		Double max = Collections.max(shorterDist);
		Double min = Collections.min(shorterDist);

		//avgDistNormal = normalize(avgDist,min,max);

		return avgDist;

	}

	double normalize(double value, double min, double max) {
		return 1 - ((value - min) / (max - min));
	}


	@Override
	protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {
		long startTime = System.nanoTime();
		//String[] args = ExperimentFiltering.getArgs();
		//String[] args = ExperimentOfflineCPFilter.getArgs();
		String[] args = ExpFilter.getArgs();
		int pNo = Integer.parseInt(args[0]);

		setFilterParam(args[5]);

		Learner learner = (Learner) getPreparedClassOption(this.learnerOption);
		ExampleStream stream = (ExampleStream) getPreparedClassOption(this.streamOption);
		LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(
				this.evaluatorOption);
		LearningCurve learningCurve = new LearningCurve("learning evaluation instances");

		//hash containing the results to be dumped to a file at the end
		hashResults.put(RES_INSTANCE_CLASS, new Vector<Integer>());
		hashResults.put(RES_INSTANCE_PREDICTION, new Vector<Integer>());
		hashResults.put(RES_RECALL_0, new Vector<Double>());
		hashResults.put(RES_RECALL_1, new Vector<Double>());
		hashResults.put(RES_TS, new Vector<String>());
		hashResults.put(RES_TELAPSD, new Vector<String>());

		// New for prequential methods
		if (evaluator instanceof FadingFactorClassificationPerformanceEvaluator) {
			// ((FadingFactorClassificationPerformanceEvaluator)
			// evaluator).setalpha(alphaOption.getValue());
			if (alphaOption.getValue() != .01) {
				System.out.println(
						"DEPRECATED! Use EvaluatePrequential -e (FadingFactorClassificationPerformanceEvaluator -a "
								+ alphaOption.getValue() + ")");
				return learningCurve;
			}
		}
		// End New for prequential methods

		learner.setModelContext(stream.getHeader());
		int maxInstances = this.instanceLimitOption.getValue();
		long instancesProcessed = 0;
		int maxSeconds = this.timeLimitOption.getValue();
		int secondsElapsed = 0;
		monitor.setCurrentActivity("Evaluating learner...", -1.0);

		// File for output predictions
		File outputPredictionFile = this.outputPredictionFileOption.getFile();
		PrintStream outputPredictionResultStream = null;
		if (outputPredictionFile != null) {
			try {
				if (outputPredictionFile.exists()) {
					outputPredictionResultStream = new PrintStream(new FileOutputStream(outputPredictionFile, true),
							true);
				} else {
					outputPredictionResultStream = new PrintStream(new FileOutputStream(outputPredictionFile), true);
				}
			} catch (Exception ex) {
				throw new RuntimeException("Unable to open prediction result file: " + outputPredictionFile, ex);
			}
		}

		//System.out.println(Integer.parseInt(args[0]));

		while (stream.hasMoreInstances() && ((maxInstances < 0) || (instancesProcessed < maxInstances))
				&& ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {

			boolean write_results = true;
			boolean cpTrain =  false;



			Example trainInst = stream.nextInstance();
			Example testInst = (Example) trainInst.copy();

			Attribute a = new Attribute("commit_type");
			int commit_type = (int) ((Instance) trainInst.getData()).value(a);

			Attribute b = new Attribute("project_no");
			int project_no = (int) ((Instance) trainInst.getData()).value(b);

			boolean trainWith = false;
			trainWith = filterInst(trainInst,(ArrayList<Example>) window,project_no,pNo);

			if(!trainWith) {
				//System.out.println("ppp"+trainWith);
				if(!CPQ.contains(trainInst)) {
					if(CPQ.size()>=cpqSize) {
						CPQ.remove(0);
					}
					CPQ.add(trainInst);
					trainWithInst=false;
				}
			}
			int predictedClass = 0;

			if(trainWith) {	
				trw++;

				((Instance) trainInst.getData()).deleteAttributeAt(17);
				((Instance) testInst.getData()).deleteAttributeAt(17);

				((Instance) trainInst.getData()).deleteAttributeAt(16);
				((Instance) testInst.getData()).deleteAttributeAt(16);

				if (commit_type == NOT_BUG) {
					if(project_no==pNo) {
						double[] prediction = learner.getVotesForInstance(testInst);

						if (prediction.length > 1) {
							if (prediction[0] > prediction[1]) {
								predictedClass = 0;
							} else {
								predictedClass = 1;
							}
						} else {
							predictedClass = 0;
						}
						// Output prediction
						if (outputPredictionFile != null) {
							int trueClass = (int) ((Instance) trainInst.getData()).classValue();

							outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
									+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
						}
						evaluator.addResult(testInst, prediction);
					}
					else {
						write_results = false;
					}
					learner.trainOnInstance(trainInst);
				}

				// if test instance and bug not found within w days, then test and train as non-defective.
				if (commit_type == BUG_NOT_DISCOVERED_W_DAYS_TEST) {
					((Instance) testInst.getData()).setClassValue(1);
					double[] prediction = learner.getVotesForInstance(testInst);

					if (prediction.length > 1) {
						if (prediction[0] > prediction[1]) {
							predictedClass = 0;
						} else {
							predictedClass = 1;
						}
					} else {
						predictedClass = 0;
					}

					// Output prediction
					if (outputPredictionFile != null) {
						int trueClass = (int) ((Instance) testInst.getData()).classValue();

						outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
								+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
					}
					evaluator.addResult(testInst, prediction);
					((Instance) trainInst.getData()).setClassValue(0);
					learner.trainOnInstance(trainInst);
				}

				// if test instance and bug FOUND within w days, then test as defective.
				if (commit_type == BUG_DISCOVERED_W_DAYS_TEST) {
					((Instance) testInst.getData()).setClassValue(1);
					double[] prediction = learner.getVotesForInstance(testInst);

					if (prediction.length > 1) {
						if (prediction[0] > prediction[1]) {
							predictedClass = 0;
						} else {
							predictedClass = 1;
						}
					} else {
						predictedClass = 0;
					}

					// Output prediction
					if (outputPredictionFile != null) {
						int trueClass = (int) ((Instance) trainInst.getData()).classValue();

						outputPredictionResultStream.println(Utils.maxIndex(prediction) + ","
								+ (((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
					}
					evaluator.addResult(testInst, prediction);
				}

				// If bug found, then train.
				if (commit_type == BUG_FOUND) {
					((Instance) trainInst.getData()).setClassValue(1);
					learner.trainOnInstance(trainInst);
					write_results = false;
				}

				// If non-test instance and bug not found within w days, then only train as non-defective. 
				// Do not test.
				if (commit_type == BUG_NOT_DISCOVERED_W_DAYS_NOT_TEST) {
					((Instance) trainInst.getData()).setClassValue(0);
					learner.trainOnInstance(trainInst);
					write_results = false;
				}

				Iterator<Example> iter = CPQ.iterator();
				while (iter.hasNext()) {
					Example cpInst = iter.next();
					//System.out.println("cpq size "+CPQ.size());
					Attribute cp_b = new Attribute("project_no");
					int cp_project_no = (int) ((Instance) cpInst.getData()).value(cp_b);
					cpTrain = filterInst(cpInst,(ArrayList<Example>) window,cp_project_no,pNo);

					if(cpTrain) {
						((Instance) cpInst.getData()).deleteAttributeAt(17);
						((Instance) cpInst.getData()).deleteAttributeAt(16);
						learner.trainOnInstance(cpInst);
						iter.remove();
					}

				}	

				if (project_no==pNo && write_results) {
					instancesProcessed++;
					//System.out.println(instancesProcessed);
					if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0
							|| stream.hasMoreInstances() == false) {

						String ts = (((Instance) testInst.getData()).value(15) + "");
						int idxE = ts.indexOf("E");

						ts = new BigDecimal(((Instance) testInst.getData()).value(15) + "").intValue()+"";

						if(idxE > 0){

							((Vector<String>) hashResults.get(RES_TS))
							.add(ts);

							((Vector<Integer>) hashResults.get(RES_INSTANCE_CLASS))
							.add(new Double(((Instance) testInst.getData()).classValue()).intValue());
							((Vector<Integer>) hashResults.get(RES_INSTANCE_PREDICTION)).add(predictedClass);
							((Vector<Double>) hashResults.get(RES_RECALL_0))
							.add(evaluator.getPerformanceMeasurements()[7].getValue());
							((Vector<Double>) hashResults.get(RES_RECALL_1))
							.add(evaluator.getPerformanceMeasurements()[8].getValue());	

						}

					}
				}

			}



		}
		// print output file
		String filePath = dumpFileOption.getValue();
		try (PrintWriter writer = new PrintWriter(new File(filePath))) {

			//compute gmean and difference between recalls
			DecimalFormat df = new DecimalFormat("#0.00"); 

			StringBuilder sb = new StringBuilder();
			sb.append("instance class,");
			sb.append("predicted class,");
			sb.append("recall(0),");
			sb.append("recall(1),");
			sb.append("avg-gmean,");
			sb.append("timestamp,");
			sb.append("total timeelapsed in nanoseconds,");
			sb.append("total timeelapsed in seconds \n");

			double each_gmean = 0;
			long endTime = System.nanoTime();
			// get difference of two nanoTime values
			long timeElapsed = endTime - startTime;

			System.out.println("\nExecution time in nanoseconds  : " + timeElapsed);
			System.out.println("Execution time in seconds : " +
					timeElapsed / (1000000*1000));
			long telapsed_nano = timeElapsed ;
			long telapsed_second = timeElapsed / (1000000*1000);

			for(int i = 0; i < ((Vector<Integer>) hashResults.get(RES_INSTANCE_CLASS)).size(); i++){
				each_gmean = Math.sqrt(((Vector<Double>) hashResults.get(RES_RECALL_1)).get(i) * ((Vector<Double>) hashResults.get(RES_RECALL_0)).get(i));

				sb.append(((Vector<Integer>) hashResults.get(RES_INSTANCE_CLASS)).get(i)+",");
				sb.append(((Vector<Integer>) hashResults.get(RES_INSTANCE_PREDICTION)).get(i)+",");
				sb.append(((Vector<Double>) hashResults.get(RES_RECALL_0)).get(i)+",");
				sb.append(((Vector<Double>) hashResults.get(RES_RECALL_1)).get(i)+",");
				sb.append(each_gmean+",");
				sb.append(((Vector<String>) hashResults.get(RES_TS)).get(i)+",");
				sb.append(String.valueOf(telapsed_nano)+",");
				sb.append(String.valueOf(telapsed_second)+"\n");
			}

			writer.write(sb.toString());

			//compute summary of the results
			OptionalDouble averageRec0 = ((Vector<Double>) hashResults.get(RES_RECALL_0)).stream()
					.mapToDouble(a -> a)
					.average();

			OptionalDouble averageRec1 = ((Vector<Double>) hashResults.get(RES_RECALL_1)).stream()
					.mapToDouble(a -> a)
					.average();

			//				//compute gmean and difference between recalls
			//				
			double avg_gmean = 0.;
			double avg_diffRecalls = 0.;

			for(int i = 0; i < ((Vector<Double>) hashResults.get(RES_RECALL_1)).size(); i++){
				avg_gmean += Math.sqrt(((Vector<Double>) hashResults.get(RES_RECALL_1)).get(i) * ((Vector<Double>) hashResults.get(RES_RECALL_0)).get(i));
				avg_diffRecalls += Math.abs(((Vector<Double>) hashResults.get(RES_RECALL_1)).get(i) - ((Vector<Double>) hashResults.get(RES_RECALL_0)).get(i));
			}

			avg_diffRecalls /= ((Vector<Double>) hashResults.get(RES_RECALL_1)).size();
			avg_gmean /= ((Vector<Double>) hashResults.get(RES_RECALL_1)).size();

			//DecimalFormat df = new DecimalFormat("#0.00");  

			System.out.println("\nResults*******\n");
			System.out.println("Tested Instances: "+ ((Vector<Double>) hashResults.get(RES_RECALL_1)).size());
			System.out.println("Average Recall(0): "+df.format(averageRec0.getAsDouble()));
			System.out.println("Average Recall(1): "+df.format(averageRec1.getAsDouble()));
			System.out.println("Average Diff_recalls: "+df.format(avg_diffRecalls));
			System.out.println("Average Gmean between recalls: "+df.format(avg_gmean));



		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		}
		long endTime = System.nanoTime();
		// get difference of two nanoTime values
		long timeElapsed = endTime - startTime;

		System.out.println("\nExecution time in nanoseconds  : " + timeElapsed);
		System.out.println("Execution time in seconds : " +
				timeElapsed / (1000000*1000));

		return learningCurve;
	}
}
