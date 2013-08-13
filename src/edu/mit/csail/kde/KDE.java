package edu.mit.csail.kde;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class KDE {

	/** The collection used to store the weighted values. */
	protected TreeMap<Double, Double> m_TM = new TreeMap<Double, Double>();

	/** The weighted sum of values */
	protected double m_WeightedSum = 0;

	/** The weighted sum of squared values */
	protected double m_WeightedSumSquared = 0;

	/** The weight of the values collected so far */
	protected double m_SumOfWeights = 0;

	/** The current bandwidth (only computed when needed) */
	protected double m_Width = Double.MAX_VALUE;

	protected double python_width = Double.MAX_VALUE;

	/** Lower bound */
	public static double m_Lower = -Double.MAX_VALUE;

	/** Upper bound */
	public static double m_Upper = Double.MAX_VALUE;

	/** Constant for Gaussian density. */
	public static final double CONST = -0.5 * Math.log(2 * Math.PI);

	/** Threshold at which further kernels are no longer added to sum. */
	protected double m_Threshold = 1.0E-6;

	/**
	 * KDE constructor. Set up the width, lower, and upper bound.
	 * 
	 * @param bw
	 * @param lower
	 * @param upper
	 */
	public KDE(double bw, double lower, double upper) {
		m_Lower = lower;
		m_Upper = upper;
		m_Width = bw;
	}

	/**
	 * KDE constructor. Set up the width, lower, and upper bound.
	 * 
	 * @param bw
	 * @param lower
	 * @param upper
	 */
	public KDE(double bw, double lower) {
		m_Lower = lower;
		m_Width = bw;
	}

	/**
	 * KDE constructor.
	 * 
	 * @param bw
	 * @param lower
	 * @param upper
	 */
	public KDE(double bw) {
		m_Width = bw;
	}

	/**
	 * Adds a data point to the density estimator.
	 * 
	 * @param value
	 *            the data point to add
	 * @param weight
	 *            the weight of the value
	 */
	public void addValue(double value, double weight) {

		m_WeightedSum += value * weight;
		m_WeightedSumSquared += value * value * weight;
		m_SumOfWeights += weight;
		if (m_TM.get(value) == null) {
			m_TM.put(value, weight);
		} else {
			m_TM.put(value, m_TM.get(value) + weight);
		}
	}

	public void updateWidth() {

		if (m_SumOfWeights > 0) {
			// Compute variance for scaling
			double mean = m_WeightedSum / m_SumOfWeights;
			double variance = m_WeightedSumSquared / m_SumOfWeights - mean
					* mean;
			if (variance < 0) {
				variance = 0;
			}

			// Compute kernel bandwidth
			python_width = Math.sqrt(variance) * m_Width;
		}
	}

	/**
	 * Compute the pdf of the point (does not consider the boundary)
	 * 
	 * @param point
	 * @return
	 */
	public double evaluate_unbounded(double point) {

		// Iterate through data values
		Iterator<Map.Entry<Double, Double>> itr = m_TM.entrySet().iterator();
		double sum = 0.0;
		while (itr.hasNext()) {
			Map.Entry<Double, Double> entry = itr.next();

			// Skip entry if weight is zero because it cannot contribute to sum
			if (entry.getValue() > 0) {
				double z = (point - entry.getKey()) / m_Width;
				double terms = kernel(z);
				terms *= entry.getValue() / m_Width;
				sum += terms;
			}
		}

		return sum / (m_SumOfWeights);
	}

	/**
	 * Based on python's implementation
	 * https://github.com/scipy/scipy/blob/v0.12.0/scipy/stats/kde.py#L42
	 * 
	 * @return
	 */
	public double evaluate_python_unbounded(double point) {
		updateWidth();
		// Iterate through data values
		Iterator<Map.Entry<Double, Double>> itr = m_TM.entrySet().iterator();
		double sum = 0.0;
		while (itr.hasNext()) {
			Map.Entry<Double, Double> entry = itr.next();

			if (entry.getValue() > 0) {
				double z = (point - entry.getKey()) / python_width;
				double terms = kernel(z);
				terms *= entry.getValue() / python_width;
				sum += terms;
			}
		}
		return sum / (m_SumOfWeights);
	}

	/**
	 * Approximate normal distribution's cdf (1/2 * [1 + erf(x/sqrt(2))]) Based
	 * on https://en.wikipedia.org/wiki/Normal_distribution
	 */
	public double approx_cdf(double z) {

		return 0.5 * (1.0 + erf(z / (Math.sqrt(2.0))));
	}

	/**
	 * ERF (Gaussian Error Function) fractional error in math formula less than
	 * 1.2 * 10 ^ -7. although subject to catastrophic cancellation when z in
	 * very close to 0 from Chebyshev fitting formula for erf(z) from Numerical
	 * Recipes, 6.2
	 */
	public double erf(double z) {
		double t = 1.0 / (1.0 + 0.5 * Math.abs(z));

		// use Horner's method
		double ans = 1
				- t
				* Math.exp(-z
						* z
						- 1.26551223
						+ t
						* (1.00002368 + t
								* (0.37409196 + t
										* (0.09678418 + t
												* (-0.18628806 + t
														* (0.27886807 + t
																* (-1.13520398 + t
																		* (1.48851587 + t
																				* (-0.82215223 + t * (0.17087277))))))))));
		if (z >= 0)
			return ans;
		else
			return -ans;
	}

	/**
	 * Evaluate bounded KDE probability using renormalization
	 * 
	 * @param point
	 * @return
	 */
	public double evaluate_renorm(double point) {

		// Iterate through data values
		Iterator<Map.Entry<Double, Double>> itr = m_TM.entrySet().iterator();
		double sum = 0.0;
		while (itr.hasNext()) {
			Map.Entry<Double, Double> entry = itr.next();

			// Skip entry if weight is zero because it cannot contribute to sum
			if (entry.getValue() > 0) {
				double l = (m_Lower - entry.getKey()) / m_Width; // normalize
				double u = (m_Upper - entry.getKey()) / m_Width;

				double z = (point - entry.getKey()) / m_Width;
				double a1 = approx_cdf(u) - approx_cdf(l);
				double terms = kernel(z) * ((entry.getValue() / m_Width) / a1);
				sum += terms;
			}
		}
		return sum / m_SumOfWeights;
	}
	
	/**
	 * Evaluate bounded KDE probability using renormalization with python
	 * 
	 * @param point
	 * @return
	 */
	public double evaluate_python_renorm(double point) {
		updateWidth();
		// Iterate through data values
		Iterator<Map.Entry<Double, Double>> itr = m_TM.entrySet().iterator();
		double sum = 0.0;
		while (itr.hasNext()) {
			Map.Entry<Double, Double> entry = itr.next();

			// Skip entry if weight is zero because it cannot contribute to sum
			if (entry.getValue() > 0) {
				double l = (m_Lower - entry.getKey()) / python_width; // normalize
				double u = (m_Upper - entry.getKey()) / python_width;

				double z = (point - entry.getKey()) / python_width;
				double a1 = approx_cdf(u) - approx_cdf(l);
				double terms = kernel(z) * ((entry.getValue() / python_width) / a1);
				sum += terms;
			}
		}
		return sum / m_SumOfWeights;
	}
	
	/**
	 * Gaussian kernel
	 * 
	 * @param input
	 *            to the kernel
	 * @return
	 */
	public double kernel(double z) {
		return Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
	}

	// Not being used.. return Phi(z, mu, sigma) = Gaussian cdf with mean mu and
	// stddev sigma
	public double taylor_cdf(double z, double mu, double sigma) {
		return Phi((z - mu) / sigma);
	}

	// Not being used...return Phi(z) = standard Gaussian cdf using Taylor
	// approximation
	public double Phi(double z) {
		if (z < -8.0)
			return 0.0;
		if (z > 8.0)
			return 1.0;
		double sum = 0.0, term = z;
		for (int i = 3; sum + term != sum; i += 2) {
			sum = sum + term;
			term = term * z * z / i;
		}
		return 0.5 + sum * kernel(z);
	}

	/**
	 * Returns the natural logarithm of the density estimate at the given point.
	 * 
	 * @param value
	 *            the value at which to evaluate
	 * @return the natural logarithm of the density estimate at the given value
	 */
	public double logDensity(double value) {
		// Array used to keep running sums
		double[] sums = new double[2];
		sums[0] = Double.NaN;
		sums[1] = Double.NaN;
		runningLogSum(m_TM.tailMap(value).entrySet(), value, sums);
		return sums[0] - Math.log(m_SumOfWeights);
	}

	/**
	 * Returns textual description of this estimator.
	 */
	public String toString() {

		return "Kernel estimator with bandwidth " + m_Width
				+ " and total weight " + m_SumOfWeights + " based on\n"
				+ m_TM.toString();
	}

	/**
	 * Compute running sum of density values and weights.
	 * 
	 * private void runningSum(Set<Map.Entry<Double,Double>> c, double value,
	 * double[] sums){ Iterator<Map.Entry<Double,Double>> itr = c.iterator();
	 * while(itr.hasNext()) { Map.Entry<Double,Double> entry = itr.next(); if
	 * (entry.getValue() > 0) { double diff = (entry.getKey() - value) /
	 * m_Width; } } }
	 */

	/**
	 * Compute running log sum of density values and weights.
	 */
	private void runningLogSum(Set<Map.Entry<Double, Double>> c, double value,
			double[] sums) {

		// Auxiliary variables
		double offset = CONST - Math.log(m_Width);
		double logFactor = Math.log(m_Threshold) - Math.log(1 - m_Threshold);
		double logSumOfWeights = Math.log(m_SumOfWeights);

		// Iterate through values
		Iterator<Map.Entry<Double, Double>> itr = c.iterator();
		while (itr.hasNext()) {
			Map.Entry<Double, Double> entry = itr.next();

			// Skip entry if weight is zero because it cannot contribute to sum
			if (entry.getValue() > 0) {
				double diff = (entry.getKey() - value) / m_Width;
				double logDensity = offset - 0.5 * diff * diff;
				double logWeight = Math.log(entry.getValue());
				sums[0] = logOfSum(sums[0], logWeight + logDensity);
				sums[1] = logOfSum(sums[1], logWeight);

				// Can we stop assuming worst case?
				if (logDensity + logSumOfWeights < logOfSum(
						logFactor + sums[0], logDensity + sums[1])) {
					break;
				}
			}
		}
	}

	/**
	 * Computes the logarithm of x and y given the logarithms of x and y.
	 * 
	 * This is based on Tobias P. Mann's description in "Numerically Stable
	 * Hidden Markov Implementation" (2006).
	 */
	protected double logOfSum(double logOfX, double logOfY) {

		// Check for cases where log of zero is present
		if (Double.isNaN(logOfX)) {
			return logOfY;
		}
		if (Double.isNaN(logOfY)) {
			return logOfX;
		}

		// Otherwise return proper result, taken care of overflows
		if (logOfX > logOfY) {
			return logOfX + Math.log(1 + Math.exp(logOfY - logOfX));
		} else {
			return logOfY + Math.log(1 + Math.exp(logOfX - logOfY));
		}
	}
}
