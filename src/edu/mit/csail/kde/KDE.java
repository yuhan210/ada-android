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

	/** The current bandwidth (only computed when needed) */
	protected double m_Width = Double.MAX_VALUE;
	
	/** The weight of the values collected so far */
	protected double m_SumOfWeights = 0;

	/** Constant for Gaussian density. */
	public static final double CONST = - 0.5 * Math.log(2 * Math.PI);

	/** Threshold at which further kernels are no longer added to sum. */
	protected double m_Threshold = 1.0E-6;
	
	
	/**
	  * Adds a data point to the density estimator.
	  *
	  * @param value the data point to add
	  * @param weight the weight of the value
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
	 
	 
	 /**
	   * Returns the natural logarithm of the density estimate at the given
	   * point.
	   *
	   * @param value the value at which to evaluate
	   * @return the natural logarithm of the density estimate at the given
	   * value
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

	    return "Kernel estimator with bandwidth " + m_Width + 
	      " and total weight " + m_SumOfWeights +
	      " based on\n" + m_TM.toString();
	  }
	/**
	 *  Compute running sum of density values and weights.
	 */
	private void runningSum(Set<Map.Entry<Double,Double>> c, double value, 
								double[] sums){
		 Iterator<Map.Entry<Double,Double>> itr = c.iterator();
		 while(itr.hasNext()) {
		      Map.Entry<Double,Double> entry = itr.next();
		      if (entry.getValue() > 0) {
			        double diff = (entry.getKey() - value) / m_Width;
			  }
		 }
	}
	  
	/**
	 * Compute running log sum of density values and weights.
	 */
	private void runningLogSum(Set<Map.Entry<Double,Double>> c, double value, 
	                            double[] sums) {

	    // Auxiliary variables
	    double offset = CONST - Math.log(m_Width);
	    double logFactor = Math.log(m_Threshold) - Math.log(1 - m_Threshold);
	    double logSumOfWeights = Math.log(m_SumOfWeights);

	    // Iterate through values
	    Iterator<Map.Entry<Double,Double>> itr = c.iterator();
	    while(itr.hasNext()) {
	      Map.Entry<Double,Double> entry = itr.next();

	      // Skip entry if weight is zero because it cannot contribute to sum
	      if (entry.getValue() > 0) {
	        double diff = (entry.getKey() - value) / m_Width;
	        double logDensity = offset - 0.5 * diff * diff;
	        double logWeight = Math.log(entry.getValue());
	        sums[0] = logOfSum(sums[0], logWeight + logDensity);
	        sums[1] = logOfSum(sums[1], logWeight);

	        // Can we stop assuming worst case?
	        if (logDensity + logSumOfWeights < logOfSum(logFactor + sums[0], logDensity + sums[1])) {
	          break;
	        }
	      }
	    }
	  }
	
	/**
	 * Computes the logarithm of x and y given the logarithms of x and y.
	 *
	 * This is based on Tobias P. Mann's description in "Numerically
	 * Stable Hidden Markov Implementation" (2006).
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
