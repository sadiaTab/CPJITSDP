<h1>Cross-Project Online Just-In-Time Software
Defect Prediction 
</h1>

The repository contains:
<ul>
  <li>Java implementation of online cross-project approaches proposed in "An Investigation of Cross-Project Learning in Online
Just-In-Time Software Defect Prediction" (ICSE'20) and "Cross-Project Online Just-In-Time Software
  Defect Prediction" (TSE'22). </li>
  <li>Opensource datasets used for the experiments and hyper-parameter tuning.</li>
  </ul>
  
  <h2>Abstract</h2>
Cross-Project (CP) Just-In-Time Software Defect Prediction (JIT-SDP) makes use of CP data to overcome the lack of data necessary to train well performing JIT-SDP classifiers at the beginning of software projects. However, such approaches have never been investigated in realistic online learning scenarios, where Within-Project (WP) software changes naturally arrive over time and can be used to automatically update the classifiers. We provide the first investigation of when and to what extent CP data are useful for JIT-SDP in such realistic scenarios. For that, we propose three different online CP JIT-SDP approaches that can be updated with incoming CP and WP training examples over time. We also collect data on 9 proprietary software projects and use 10 open source software projects to analyse these approaches. We find that training classifiers with incoming CP+WP data can lead to absolute
improvements in G-mean of up to 53.89% and up to 35.02% at the initial stage of the projects compared to classifiers using WP-only and CP-only data, respectively. Using CP+WP data was also shown to be beneficial after a large number of WP data were received. Using CP data to supplement WP data helped the classifiers to reduce or prevent large drops in predictive performance that may occur over time, leading to absolute G-Mean improvements of up to 37.35% and 48.16% compared to WP-only and CP-only data during such periods, respectively. During periods of stable predictive performance, absolute improvements were of up to 29.03% and up to 41.25% compared to WP-only and CP-only classifiers, respectively. Our results highlight the importance of using both CP and WP data together in realistic online JIT-SDP scenarios.

<h3>Authors:</h3>
<ul>
  <li>Sadia Tabassum (sxt901 at student dot bham dot ac dot uk)</li>
  <li>Leandro Minku (L dot L dot Minku at bham dot ac dot uk)</li>
</ul>

<h3>Environment details:</h3>
<ul>
  <li>MOA 2018.6.0</li>
  <li>JDK and JRE 1.8</li>
</ul>

<h2>To run experiments for Online CPJITSDP</h2>
<ul>
  <li>Go to the directory src/cpjitsdpexperiment</li>
  <li>There are 4 experiment files- ExpAIO, ExpFilter, ExpOPAIO and ExpOPFilter for online cpjitsdp approaches (AIO, Filter, OPAIO and OPFilter, respectively).</li>
  <li>Run appropriate experiment file (i.e. cpjitsdpexperiment.ExpAIO.java)</li>
</ul>

Example command (can be found in the experiment files):

```
CpjitsdpAIO -l (spdisc.meta.WFL_OO_ORB_Oza -i 15 -s "+ens+" -t "+theta+" -w "+waitingTime+" -p "+paramsORB+")  -s  (ArffFileStream -f (datasets/tomcat.arff) -c 15) -e (FadingFactorEachClassPerformanceEvaluator -a 0.99) -f 1 -d results/results.csv"
```
<ul>
  <li>CpjitsdpAIO: Online CPJITSDP approach to run.</li>
  <li>-i 15 - the position of the unixtimestamp of the commit in the arff</li>
  <li>-s - the ensemble size</li>
  <li>-t - the fading factor used for computing the class sizes</li>
  <li>-w - the waiting time for assuming the commit label is available</li>
  <li>-p - the parameters for the ORB.</li>
  <li>Values for -s,-t,-w and -p can be passed as arguments. </li>
  <li>Default values for -s,-t,-w and -p are (20,0.99,90 and 100;0.4;10;12;1.5;3)</li>
</ul>

MOA parameters:
<ul>
  <li>-l the machine learning algorithm to be used.</li>

<li>-s (ArffFileStream -f -c ) is the path to the dataset in arff format, with -c indicating the index of the class label in the dataset file.</li>

<li>-e (FadingFactorEachClassPerformanceEvaluator -a ) is the performance evaluator to be used, with -a indicating the fading factor to be adopted.</li>

<li>-d is the path to the output file where the results of the experiments will be saved.</li>
</ul>

<h2>Datasets</h2>
Datasets used in the experiments are in ARFF format. The ARFF file must contain header with the following attributes and must maintain the order of the attributes.

```

    @attribute fix {False,True}
    @attribute ns numeric
    @attribute nd numeric
    @attribute nf numeric
    @attribute entrophy numeric
    @attribute la numeric
    @attribute ld numeric
    @attribute lt numeric
    @attribute ndev numeric
    @attribute age numeric
    @attribute nuc numeric
    @attribute exp numeric
    @attribute rexp numeric
    @attribute sexp numeric
    @attribute contains_bug {False,True}
    @attribute author_date_unix_timestamp numeric
    @attribute project_no numeric
    @attribute commit_type numeric
    @data
    
```

<ul>
<li>
Attributes[1-14]: Software change metrics.</li>
<li>
Attribute[15]: True label of the commit (whether the commit is really defect-inducing or clean).
</li>
<li>
Attribute[16]: Timestamp when the commit was submitted to the repository. 
</li>
<li>Attribute[17]: Index number associated to a project. For example, in this paper, project_no for Tomcat is 0 and JGroups is 1. </li>
<li>Attribute[18]: commit_type is a number assigned based on the following scenario:
  <ol>
    <li>The commit x is not buggy. 
      <ol><li>Update timstamp of commit "x" by adding verification latency (waiting period W).</li>
        <li>Assign commit_type 0</li>
        <li> "x" will be tested only if it is from target project and will be trained as clean</li>
      </ol>
    </li>
    <li>For target project, the commit is buggy but its true label was not found within W days.
      <ol>
        <li>Update timstamp of commit "x" by adding verification latency (waiting period W).</li>
        <li>Create duplicate of the commit, "x^"; update timestamp of "x^" by adding days_to_fix and assign label defect-inducing.</li> 
        <li>Assign commit_type 1</li>
        <li>"x" will be tested and will be trained as clean
      </ol>
    </li>
    <li>For target project, the commit is buggy and its true label was found within W days.
      <ol>
        <li>Update timstamp of commit "x" by adding verification latency (waiting period W).</li>
        <li>Assign commit_type 2</li>
        <li>"x" will be tested</li>
      </ol>
    </li>
    <li>For cross projects, the commit "x" is buggy but its true label was not found within W days.
      <ol>
        <li>Assign commit_type 4</li>
        <li>Create duplicate of the commit, "x^"; update timestamp of "x^" by adding days_to_fix and assign label defect-inducing.</li> 
        <li>"x" will be trained as clean</lI>
      </ol>
    </li>
    <li>The true label of a defective commit was assigned (for all projects)
      <ol>
        <li>Assign commit_type 3</li>
        <li>"x" will be trained as defect-inducing</li>
      </ol>
    </li>
  </ol>
</ul>

