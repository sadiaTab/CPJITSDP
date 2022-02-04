<h1>Cross-Project Online Just-In-Time Software
Defect Prediction 
(Online CPJITSDP)
</h1>

The repository contains:
<ul>
  <li>Java implementation of online cross-project approaches introduced in "An Investigation of Cross-Project Learning in Online
Just-In-Time Software Defect Prediction" (ICSE'20) and "Cross-Project Online Just-In-Time Software
  Defect Prediction" (TSE'22). </li>
  <li>Opensource datasets used for the experiments and hyper-parameter tuning.</li>
  </ul>
  
  <h2>Abstract</h2>
  Cross-Project (CP) Just-In-Time Software Defect Prediction (JIT-SDP) makes use of CP data to overcome the lack of data
necessary to train well performing JIT-SDP classifiers at the beginning of software projects. However, such approaches have never
been investigated in realistic online learning scenarios, where Within-Project (WP) software changes naturally arrive over time and can
be used to automatically update the classifiers. We provide the first investigation of when and to what extent CP data are useful for
JIT-SDP in such realistic scenarios. For that, we propose three different online CP JIT-SDP approaches that can be updated with
incoming CP and WP training examples over time. We also collect data on 9 proprietary software projects and use 10 open source
software projects to analyse these approaches. We find that training classifiers with incoming CP+WP data can lead to absolute
improvements in G-mean of up to 53.89% and up to 35.02% at the initial stage of the projects compared to classifiers using WP-only
and CP-only data, respectively. Using CP+WP data was also shown to be beneficial after a large number of WP data were received.
Using CP data to supplement WP data helped the classifiers to reduce or prevent large drops in predictive performance that may occur
over time, leading to absolute G-Mean improvements of up to 37.35% and 48.16% compared to WP-only and CP-only data during such
periods, respectively. During periods of stable predictive performance, absolute improvements were of up to 29.03% and up to 41.25%
compared to WP-only and CP-only classifiers, respectively. Our results highlight the importance of using both CP and WP data
together in realistic online JIT-SDP scenarios.
