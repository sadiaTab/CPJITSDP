package moa.evaluation;

import com.github.javacliparser.FloatOption;

public class FadingFactorEachClassPerformanceEvaluator extends EachClassPerformanceEvaluator {

    private static final long serialVersionUID = 1L;

    public FloatOption alphaOption = new FloatOption("alpha",
            'a', "Fading factor or exponential smoothing factor", .999);

    @Override
    protected Estimator newEstimator() {
        return new FadingFactorEstimator(this.alphaOption.getValue());
    }

    public class FadingFactorEstimator implements Estimator {

		private static final long serialVersionUID = 1L;

		protected double alpha;

        protected double estimation;

        protected double b;

        public FadingFactorEstimator(double a) {
            alpha = a;
            estimation = 0.0;
            b = 0.0;
        }

        @Override
        public void add(double value) {
            estimation = alpha * estimation + value;
            b = alpha * b + 1.0;
        }

        @Override
        public double estimation() {
            return b > 0.0 ? estimation / b : 0;
        }

    }

}
