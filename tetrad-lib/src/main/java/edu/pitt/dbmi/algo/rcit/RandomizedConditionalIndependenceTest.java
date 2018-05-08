/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.algo.rcit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

/**
 * 
 * Apr 10, 2018 5:10:44 PM
 * 
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class RandomizedConditionalIndependenceTest implements IndependenceTest {

	/**
	 * The data set this test uses.
	 */
	private DataSet dataSet;

	private RandomIndApproximateMethod approx = RandomIndApproximateMethod.lpd4;

	private int num_feature = 25;

	private double alpha = 0.01;

	private double pValue = Double.NaN;
	
	private double statistic = Double.NaN;

	public RandomizedConditionalIndependenceTest(final DataSet dataSet) {
		this.dataSet = dataSet;
	}

	@Override
	public IndependenceTest indTestSubset(List<Node> vars) {
		return null;
	}

	@Override
	public boolean isIndependent(Node x, Node y, List<Node> z) {

		if (z == null || z.size() == 0) {
			// RIT
		}

		DataSet varX = dataSet.subsetColumns(Collections.singletonList(x));
		List<Node> yz = new ArrayList<>();
		yz.add(y);
		yz.addAll(z);
		DataSet varY = dataSet.subsetColumns(yz);

		// Keep a list of z only its sd > 0
		List<Node> nonZeroSD_z = new ArrayList<>();
		for (Node node : z) {
			DataSet _z = dataSet.subsetColumns(Collections.singletonList(node));
			TetradMatrix m = _z.getDoubleData();
			TetradVector v = m.getColumn(0);
			double sd = StatUtils.sd(v.toArray());
			if (sd > 0) {
				nonZeroSD_z.add(node);
			}
		}
		DataSet varZ = dataSet.subsetColumns(nonZeroSD_z);

		int col = varZ.getNumColumns();

		if (col == 0) {
			// RIT
		} else if (StatUtils.sd(varX.getDoubleData().getColumn(0).toArray()) == 0
				|| StatUtils.sd(varY.getDoubleData().getColumn(0).toArray()) == 0) {
			return (this.pValue = 1) > alpha;
		}

		int r1 = 500;
		int row = varX.getNumRows();
		if (row < 500) {
			r1 = row;
		}

		// Standardize data
		TetradMatrix xMatrix = new TetradMatrix(row, 1);
		xMatrix.assignColumn(0, new TetradVector(StatUtils.standardizeData(varX.getDoubleData().getColumn(0).toArray())));
		
		TetradMatrix yMatrix = new TetradMatrix(row, yz.size());
		for (int i = 0; i < yz.size(); i++) {
			yMatrix.assignColumn(0, new TetradVector(StatUtils.standardizeData(varY.getDoubleData().getColumn(i).toArray())));
		}
		
		TetradMatrix zMatrix = new TetradMatrix(row, col);
		for (int i = 0; i < col; i++) {
			zMatrix.assignColumn(i, new TetradVector(StatUtils.standardizeData(varZ.getDoubleData().getColumn(i).toArray())));
		}

		// Randomized Fourier Features
		// sigma_z
		// sigma=median(c(t(dist(z[1:r1,]))))
		double[] dist_z = new double[(r1 - 1) * (r1) / 2];
		int k = 0;
		for (int i = 0; i < r1 - 1; i++) {
			for (int j = i + 1; i < r1; j++) {
				double[] z_x = zMatrix.getRow(i).toArray();
				double[] z_y = zMatrix.getRow(j).toArray();
				dist_z[k] = getDistance(z_x, z_y);
				k++;
			}
		}
		double sigma_z = StatUtils.median(dist_z);
		RandomFourierFeatures four_z = RandomFourierFeatures.generate(zMatrix, null, null, num_feature, sigma_z);
		// sigma_x
		// median(c(t(dist(x[1:r1,])))
		double[] dist_x = new double[(r1 - 1) * (r1) / 2];
		k = 0;
		for (int i = 0; i < r1 - 1; i++) {
			for (int j = i + 1; i < r1; j++) {
				double[] x_x = xMatrix.getRow(i).toArray();
				double[] x_y = xMatrix.getRow(j).toArray();
				dist_x[k] = getDistance(x_x, x_y);
				k++;
			}
		}
		double sigma_x = StatUtils.median(dist_x);
		RandomFourierFeatures four_x = RandomFourierFeatures.generate(xMatrix, null, null, 5, sigma_x);
		// sigma_y
		// median(c(t(dist(y[1:r1,]))))
		double[] dist_y = new double[(r1 - 1) * (r1) / 2];
		k = 0;
		for (int i = 0; i < r1 - 1; i++) {
			for (int j = i + 1; i < r1; j++) {
				double[] y_x = yMatrix.getRow(i).toArray();
				double[] y_y = yMatrix.getRow(j).toArray();
				dist_y[k] = getDistance(y_x, y_y);
				k++;
			}
		}
		double sigma_y = StatUtils.median(dist_y);
		RandomFourierFeatures four_y = RandomFourierFeatures.generate(yMatrix, null, null, 5, sigma_y);

		// Standardize randomized Fourier features
		// four_x
		TetradMatrix fxMatrix = new TetradMatrix(four_x.getFeature().rows(),four_x.getFeature().columns());
		for(int i=0;i<fxMatrix.columns();i++) {
			fxMatrix.assignColumn(i, new TetradVector(StatUtils.standardizeData(four_x.getFeature().getColumn(i).toArray())));
		}
		
		// four_y
		TetradMatrix fyMatrix = new TetradMatrix(four_y.getFeature().rows(),four_y.getFeature().columns());
		for(int i=0;i<fyMatrix.columns();i++) {
			fyMatrix.assignColumn(i, new TetradVector(StatUtils.standardizeData(four_y.getFeature().getColumn(i).toArray())));
		}
		
		// four_z
		TetradMatrix fzMatrix = new TetradMatrix(four_z.getFeature().rows(),four_z.getFeature().columns());
		for(int i=0;i<fzMatrix.columns();i++) {
			fzMatrix.assignColumn(i, new TetradVector(StatUtils.standardizeData(four_z.getFeature().getColumn(i).toArray())));
		}
		
		// Covariance Matrix f_x,f_y
		TetradMatrix cxyMatrix = new TetradMatrix(fxMatrix.columns(),fyMatrix.columns());
		for(int i=0;i<fxMatrix.columns();i++) {
			for(int j=0;j<fyMatrix.columns();j++) {
				cxyMatrix.set(i, j, StatUtils.covariance(fxMatrix.getColumn(i).toArray(), fyMatrix.getColumn(j).toArray()));
			}
		}

		// Covariance Matrix f_x,f_z
		TetradMatrix cxzMatrix = new TetradMatrix(fxMatrix.columns(),fzMatrix.columns());
		for(int i=0;i<fxMatrix.columns();i++) {
			for(int j=0;j<fzMatrix.columns();j++) {
				cxzMatrix.set(i, j, StatUtils.covariance(fxMatrix.getColumn(i).toArray(), fzMatrix.getColumn(j).toArray()));
			}
		}
		
		// Covariance Matrix f_z,f_y
		// double[][] czy = new double[f_z.length][f_y.length];
		TetradMatrix czyMatrix = new TetradMatrix(fzMatrix.columns(),fyMatrix.columns());
		for(int i=0;i<fzMatrix.columns();i++) {
			for(int j=0;j<fyMatrix.columns();j++) {
				czyMatrix.set(i, j, StatUtils.covariance(fzMatrix.getColumn(i).toArray(), fyMatrix.getColumn(j).toArray()));
			}
		}
		
		
		// Covariance matrix f_z
		// double[][] czz = new double[f_z.length][f_z.length];
		TetradMatrix czzMatrix = new TetradMatrix(fzMatrix.columns(),fzMatrix.columns());
		for(int i=0;i<fzMatrix.columns();i++) {
			for(int j=i;j<fzMatrix.columns();j++) {
				czzMatrix.set(i, j, StatUtils.covariance(fzMatrix.getColumn(i).toArray(), fzMatrix.getColumn(j).toArray()));
				if(i != j) {
					czzMatrix.set(j, i, StatUtils.covariance(fzMatrix.getColumn(i).toArray(), fzMatrix.getColumn(j).toArray()));
				}
			}
		}
		
		TetradMatrix diagFeature = new TetradMatrix(MatrixUtils.identity(num_feature));
		diagFeature = diagFeature.scalarMult(1^-10);
		czzMatrix = czzMatrix.plus(diagFeature);
		
		TetradMatrix i_czzMatrix = new TetradMatrix(MatrixUtils.inverse(czzMatrix.toArray()));
		
		TetradMatrix z_i_czzMatrix = fzMatrix.times(i_czzMatrix);
		TetradMatrix e_x_zMatrix = z_i_czzMatrix.times(cxzMatrix.transpose());
		TetradMatrix e_y_zMatrix = z_i_czzMatrix.times(czyMatrix);
		
		// Approximate null distributions
		
		TetradMatrix res_x  = fxMatrix.minus(e_x_zMatrix);
		TetradMatrix res_y  = fyMatrix.minus(e_y_zMatrix);
		
		if(approx == RandomIndApproximateMethod.perm) {
			// Covariance matrix res_x, res_y
			TetradMatrix cxy_zMatrix = new TetradMatrix(res_x.columns(),res_y.columns());
			double sum_cxy_z_squared = 0;
			for(int i=0;i<res_x.columns();i++) {
				for(int j=i;j<res_y.columns();j++) {
					double cxy_z = StatUtils.covariance(res_x.getColumn(i).toArray(), res_y.getColumn(j).toArray());
					cxy_zMatrix.set(i, j, cxy_z);
					sum_cxy_z_squared += cxy_z*cxy_z;
				}
			}
			
			this.statistic = ((double)row)*sum_cxy_z_squared;
			
			int nperm = 1000;
			
			int perm_stat_less_than_stat = 0;
			for(int perm=0;perm<nperm;perm++) {
				TetradMatrix permMatrix = new TetradMatrix(res_x.rows(),res_x.columns());
				List<Integer> perm_order = new ArrayList<>();
				for(int i=0;i<res_x.rows();i++) {
					perm_order.add(i);
				}
				for(int i=0;i<permMatrix.rows();i++) {
					int _row = RandomUtil.getInstance().nextInt(perm_order.size());
					permMatrix.assignRow(i, new TetradVector(res_x.getRow(perm_order.get(_row)).toArray()));
					perm_order.remove(_row);
				}
				
				double sum_perm_res_xy_squared = 0;
				for(int i=0;i<permMatrix.columns();i++) {
					for(int j=i;j<res_y.columns();j++) {
						double cov_perm_res_y = StatUtils.covariance(permMatrix.getColumn(i).toArray(), res_y.getColumn(j).toArray());
						sum_perm_res_xy_squared += cov_perm_res_y*cov_perm_res_y;
					}
				}
				sum_perm_res_xy_squared = ((double)row)*sum_perm_res_xy_squared;
				
				if(this.statistic >= sum_perm_res_xy_squared) {
					perm_stat_less_than_stat++;
				}
			}
			
			this.pValue = 1 - (double)perm_stat_less_than_stat/nperm;
		}else {
			// Cxy_z=Cxy-Cxz%*%i_Czz%*%Czy;
			TetradMatrix cxy_zMatrix = cxyMatrix.minus(cxzMatrix.times(i_czzMatrix.times(czyMatrix)));
			double sum_cxy_z_squared = 0;
			for(int i=0;i<cxy_zMatrix.columns();i++) {
				for(int j=i;j<cxy_zMatrix.columns();j++) {
					double cov_cxy_z_squared = StatUtils.covariance(cxy_zMatrix.getColumn(i).toArray(), cxy_zMatrix.getColumn(j).toArray());
					sum_cxy_z_squared += cov_cxy_z_squared*cov_cxy_z_squared;
				}
			}
			// Sta = r*sum(Cxy_z^2);
			this.statistic = (double)row*sum_cxy_z_squared;
			
			// d =expand.grid(1:ncol(f_x),1:ncol(f_y));
			int fxMatrix_cols = fxMatrix.columns();
			int fyMatrix_cols = fyMatrix.columns();
			TetradMatrix d = new TetradMatrix(fxMatrix_cols*fyMatrix_cols, 2);
			int d_row = 0;
			for(int fy_col=0;fy_col<fyMatrix_cols;fy_col++) {
					
				for(int fx_col=0;fx_col<fxMatrix_cols;fx_col++) {
					d.set(d_row, 0, fx_col);
					d.set(d_row, 1, fy_col);
					d_row++;
				}
			}
			
			// res = res_x[,d[,1]]*res_y[,d[,2]];
			TetradMatrix res = new TetradMatrix(res_x.rows(),1);
			for(int i=0;i<res_x.rows();i++) {
				res.set(i, 0, res_x.get(i, (int)d.get(i, 0))*res_y.get(i, (int)d.get(i, 1)));
			}
			
			// Cov = 1/r * (t(res)%*%res);
			TetradMatrix covMatrix = res.transpose().times(res).scalarMult(1/(double)row);
			
			if(approx == RandomIndApproximateMethod.chi2) {
				// i_Cov = ginv(Cov)
				TetradMatrix iCovMatrix = covMatrix.inverse();
				
				// Sta = r * (c(Cxy_z)%*%  i_Cov %*% c(Cxy_z) );
			    // Flatten Cxy_z
				TetradMatrix flattenCxy_zMatrix = new TetradMatrix(1, cxy_zMatrix.rows()*cxy_zMatrix.columns());
				int index = 0;
				for(int i=0;i<cxy_zMatrix.rows();i++) {
					for(int j=0;j<cxy_zMatrix.columns();j++) {
						flattenCxy_zMatrix.set(0, index, cxy_zMatrix.get(i, j));
						index++;
					}
				}
				
				this.statistic = flattenCxy_zMatrix.times(iCovMatrix).times(flattenCxy_zMatrix).get(0, 0); 
				
				// p = 1-pchisq(Sta, length(c(Cxy_z)));
				this.pValue = 1.0 - ProbUtils.chisqCdf(this.statistic, flattenCxy_zMatrix.columns());
				
			}else {
				
				// eig_d = eigen(Cov,symmetric=TRUE);
				EigenDecomposition eigen = new EigenDecomposition(new BlockRealMatrix(covMatrix.toArray()));
				
				//  eig_d$values=eig_d$values[eig_d$values>0];
				List<Double> eig_d = new ArrayList<>();
				for(int i=0;i<eigen.getRealEigenvalues().length;i++) {
					double value = eigen.getRealEigenvalue(i);
					if(value > 0) {
						eig_d.add(value);
					}
				}
				
				
				if(approx == RandomIndApproximateMethod.gamma) {
					
					// p=1-sw(eig_d$values,Sta);
					this.pValue = 1.0 - sw(eig_d, this.statistic);
					
				}else if(approx == RandomIndApproximateMethod.hbe) {
					
					// p=1-hbe(eig_d$values,Sta);
					this.pValue = 1.0 - hbe(eig_d, this.statistic);

				}else if(approx == RandomIndApproximateMethod.lpd4) {
					
					// eig_d_values=eig_d$values;
					
					
			        // p=try(1-lpb4(eig_d_values,Sta),silent=TRUE);
					
					
			        // if (!is.numeric(p) | is.nan(p)){
			        //   p=1-hbe(eig_d$values,Sta);
			        // }
					this.pValue = 1.0 - lpd4(eig_d, this.statistic);
				}
			}
			
			
		}
		
		if(this.pValue < 0) {
			this.pValue = 0;
		}
		
		return this.pValue > alpha;
	}

	private double sw(List<Double> coeff, double x) {
		// Satterthwaite-Welch method
		// translated from sw.R, momentchi2 library
		// https://cran.r-project.org/web/packages/momentchi2/index.html
		// sw <- function(coeff, x)
		
		// compute cumulant and ratio of cumulants
		// w_val <- sum(coeff)
		// u_val <- sum(coeff^2) / (w_val^2)
		double w_val = 0;
		double u_val = 0;
		for(Double value : coeff) {
			double v = value.doubleValue();
			w_val += v;
			u_val += v*v;
		}
		u_val = u_val/(w_val*w_val);
		
		// now the G k and theta:
		// gamma_k <- 0.5 / u_val
		double gamma_k = 0.5/u_val;
		
		// gamma_theta <- 2 * u_val*w_val
		double gamma_theta = 2.0*u_val*w_val;
		
		// the actual x value
		// p_sw <- pgamma(x, shape=gamma_k, scale=gamma_theta)	
		GammaDistribution gamma = new GammaDistribution(gamma_k, gamma_theta);
		return gamma.cumulativeProbability(x);
	}
	
	private double hbe(List<Double> coeff, double x) {
		// Hall-Buckley-Eagleson method
		// translated from hbe.R, momentchi2 library
		// https://cran.r-project.org/web/packages/momentchi2/index.html
		// hbe <- function(coeff, x)
		
		// compute cumulants and nu
		// kappa <- c(sum(coeff), 2*sum(coeff^2), 8*sum(coeff^3) )
	    // Flatten eig_d list
		List<Double> kappa = new ArrayList<>();
		double k_1 = 0;
		double k_2 = 0;
		double k_3 = 0;
		for(Double value : coeff) {
			double v = value.doubleValue();
			k_1 += v;
			k_2 += v*v;
			k_3 += v*v*v;
		}
		k_2 = 2.0*k_2;
		k_3 = 8.0*k_3;
		
		kappa.add(k_1);
		kappa.add(k_2);
		kappa.add(k_3);
		
		// K_1 <- sum(coeff)
	    // K_2 <- 2 * sum(coeff^2)
	    // K_3 <- 8 * sum(coeff^3)
		// nu <- 8 * (K_2^3) / (K_3^2)
		
		double nu = 8.0*(k_2*k_2*k_2)/(k_3*k_3);
		
		// #gamma parameters for chi-square
		// gamma_k <- nu/2
		double gamma_k = nu/2.0;
		
		// gamma_theta <- 2
		double gamma_theta = 2.0;
		
		// need to transform the actual x value to x_chisqnu ~ chi^2(nu)
		// This transformation is used to match the first three moments
		// First x is normalised and then scaled to be x_chisqnu
		// x_chisqnu_vec <- sqrt(2 * nu / K_2) * (x - K_1) + nu
		double x_chisqnu_vec = Math.sqrt(2.0*nu/k_2)*(x - k_1) + nu;
		
		// now this is a chi_sq(nu) variable
		// p_chisqnu_vec <- pgamma(x_chisqnu_vec, shape=gamma_k, scale=gamma_theta)
		GammaDistribution gamma = new GammaDistribution(gamma_k, gamma_theta);
		return - gamma.cumulativeProbability(x_chisqnu_vec);
	}
	
	private double lpd4(List<Double> coeff, double x) {
		// Lindsay-Pilla-Basak method
		// Computes the cdf of a positively-weighted sum of chi-squared random variables with 
		// the Lindsay-Pilla-Basak (LPB4) method using four support points.
		// Note that the coefficient vector must be of length at least four.
		// translated from lpb4.R, momentchi2 library
		// https://cran.r-project.org/web/packages/momentchi2/index.html
		// lpb <- function(coeff, x)
		
		// Steps for computing cdf using (Lindsay, 2000) method

		// step 0.1: Obtain coefficients d_i for H = sum_i^n d_i w_i^2
		// 		 These determine the distribution

		// step 0.2: Decide on p, the number of support points to use for method
		// 		   The more support points, the more accurate (and more computationally intensive)	

		// step 1: Determine/compute the moments/cumulants m_1(H), ... m_2p(H)
		// 			First compute cumulants - sums of powers of coefficients
		// 			cf: (Wood, 1989)
		// 			Then use the cumulants to compute moments (using a recursive formula):
		// 			mu_n = kappa_n + \sum_{m=1}^{n-1} (n-1)choose(m-1) kappa_n mu_{n-m}


		// step 2.1: generate the matrices delta_i(x)

		// step 2.2: Find lambdatilde_1, the unique root of det (delta_1(x))
		// 			This does not require bisection - just a rational expression in
		// 			terms of the moments

		// step 3:	Use bisection method (R uniroot) to find lambdatilde_2 
		// 			in [0, lambdatilde_1)		
		// 			Find lambdatilde_i+1 in [0, lambdatilde_i) for i = 2,3,...p
		// 
		// 			End result: we have lambdatilde_p

		// step 4: should have this method from step 2 already, but compute
		// 			deltastar_i(lambdatilde_p) for i =1,2,...2p-1

		// step 5.1: use the deltastar_i(lambdatilde_p) from step 4 to generate
		// 			matrix Stilde(lambdatilde_p, t)
		// 
		// step 5.2:	Then need to diagonalise/use linear algerba trick in paper
		// 			to get polynomial coefficients (from det) in "coeff_vec"
		// 
		// step 5.3	use Re(polyroot(coeff_vec)) to obtain roots of polynomial
		// 			denoted mu_vec = (mu_1, ..., mu_p)	

		// step 6:	Generate Vandermonde matrix using mu_vec
		// 			and vector using deltastar_i's, to solve for
		// 			pi_vec = (pi_1, ..., pi_p)

		// step 7: 	? compute the linear combination (using pi_vec)
		// 			of the i gamma cdfs using parameters lambdatilde_p and mu_i
		// 
		// 			This should be the final answer
		
		// check if there is less than 4 elements - if so, stop
		if(coeff.size() < 4) {
			return hbe(coeff, x);
		}
		
		// step 0: decide on parameters for distribution and support points p
		// specified to be 4 for this version of the function
		int p = 4;
		
		// step 1: Determine/compute the moments m_1(H), ... m_2p(H)
		// compute the first 2p moments for Q = sum coeff chi-squared	
		// moment_vec <- get_weighted_sum_of_chi_squared_moments(coeff, p)
		
		// Step 2.1: generate matrices delta_i(x)
		// functions created:
		// deltaNmat_applied
		// and
		// det_deltamat_n
			
		// Step 2.2: get lambdatilde_1 - this method is exact (no bisection), solves determinant equation
		// lambdatilde_1 <- get_lambdatilde_1(moment_vec[1], moment_vec[2])
		
		// Step 3:	Use bisection method (R uniroot) to find lambdatilde_2 
		// and others up to lambdatilde_p, for tol=bisect_tol
		// all we need is the final lambdatilde_p, not the intermediate values lambdatilde_2, lambdatilde_3, etc
		// bisect_tol <- 1e-9
		// lambdatilde_p <- get_lambdatilde_p(lambdatilde_1, p, moment_vec, bisect_tol)
		
		// Step 4:
		// Calculate delta_star_lambda_p
		// can already do this using methods in Step 2.1 
		
		// Step 5.1: use the deltastar_i(lambdatilde_p) from Step 4 to generate
		//			M_p, which will be used to create matrix Stilde(lambdatilde_p, t)
		// M_p <- deltaNmat_applied(lambdatilde_p, moment_vec, p)
		
		// Step 5.2:	Compute polynomial coefficients of the modified M_p matrix (as in paper).
		// mu_poly_coeff_vec <- get_Stilde_polynomial_coefficients(M_p)
		
		// step 5.3	use Re(polyroot(coeff_vec)) to obtain roots of polynomial
		//			denoted mu_vec = (mu_1, ..., mu_p)	
		// mu_roots <- Re(polyroot(mu_poly_coeff_vec))
		
		// Step 6:	Generate Vandermonde matrix using mu_vec
		//			and vector using deltastar_i's, to solve for
		//			pi_vec = (pi_1, ..., pi_p)
		// pi_vec <- generate_and_solve_VDM_system(M_p, mu_roots)
		
		// Step 7: 	Compute the linear combination (using pi_vec)
		//			of the i gamma cdfs using parameters lambdatilde_p and mu_i 
		//			(but need to create scale/shape parameters carefully)
		//	
		//			This is the final answer
		// mixed_p_val_vec <- get_mixed_p_val_vec(x, mu_roots, pi_vec, lambdatilde_p)
		
		
		return Double.NaN;
	}
	
	private TetradVector get_weighted_sum_of_chi_squared_moments() {
		return null;
	}
	
	// get the cumulants kappa_1, kappa_2, ..., kappa_2p
	private TetradVector get_cumulant_vec_vectorised() {
		return null;
	}
	
	// returns the sum of the elements raised to a power
	private double sum_of_powers(int[] index, double x) {
		double sum = 0;
		for(int i = 0;i<index.length;i++) {
			sum += powers(index[i], x);
		}
		return sum;
	}
	
	private double powers(int p, double x) {
		boolean inversed = false;
		if(p < 0) {
			inversed = true;
			p = -p;
		}
		if(p == 0) {
			return 1.0;
		}
		boolean odd = false;
		if(p%2 == 1) {
			odd = true;
			p -= 1;
		}
		int height = binlog(p);
		double product = 1.0;
		for(int i=0;i<height;i++) {
			if(product == 1.0) {
				product = x*x;
			}else {
				product = product*product;
			}
		}
		if(odd) {
			product = product*x;
		}
		if(inversed) {
			product = 1/product;
		}
		return product;
	}
	
	// https://stackoverflow.com/questions/3305059/how-do-you-calculate-log-base-2-in-java-for-integers
	// 10 times faster than Math.log(x)/Math.log(2)
	private int binlog(int bits) {
		int log = 0;
	    if( ( bits & 0xffff0000 ) != 0 ) { bits >>>= 16; log = 16; }
	    if( bits >= 256 ) { bits >>>= 8; log += 8; }
	    if( bits >= 16  ) { bits >>>= 4; log += 4; }
	    if( bits >= 4   ) { bits >>>= 2; log += 2; }
	    return log + ( bits >>> 1 );
	}
	
	// get the moment vector from the cumulant vector 
	// have removed one for loop (vectorised), but can't remove the other one
	private TetradVector get_moments_from_cumulants() {
		return null;
	}
	
	// returns the sum of the additional terms/lower products of moments and cumulants
	// used in the computation of moments
	private double update_moment_from_lower_moments_and_cumulants() {
		return Double.NaN;
	}
	
	// Step 2.1: get lambdatilde_1
	// no need to use bisection method - can get lambdatilde_1 directly 
	private double get_lambdatilde_1(double m1, double m2) {
		return m2/(m1*m1) - 1;
	}
	
	// Step 2.2: generate delta_mat_N and det_delta_mat_N
	// compute the delta_N matrix - vectorised using lapply and mapply
	private TetradMatrix deltaNmat_applied(TetradMatrix x, double m_vec, int n) {
		return null;
	}
	
	// get_partial_products gets prod[1:index] 
	private double get_partial_products(int index, double[] vec) {
		double product = 1.0;
		for(int i=0;i<index;i++) {
			product *= vec[i];
		}
		return product;
	}
	
	// this function in deltaNmat_applied computes the index from i and j, and then returns the appropriate product
	// of vec1 and vec2 
	// (in deltaNmat_applied, these vectors are the moment vector and the vector of the products of the (1+N*lambda)^(-1) terms)
	private double get_index_element(int i, int j, double[] vec1, double[] vec2) {
		int index = i + j - 1;
		return vec1[index]*vec2[index];
	}
	
	// Simply uses above matrix generation function
	private double det_deltamat_n(TetradMatrix x, double m_vec, int n) {
		return Double.NaN;
	}
	
	// Step 3: get lambdatilde_p
	// uses det_delta_mat_n and uniroot
	// get lambdatilde_p by using bisection method repeatedly. 
	// Need lambdatilde_1 to start
	// Need to use R function uniroot
	private double get_lambdatilde_p() {
		return Double.NaN;
	}
	
	// Step 5.2: Compute polynomial coefficients for mu polynomial
	// We could use the linear algebra trick described in the Lindsay paper, but want to avoid
	// dealing with small eigenvalues. Instead, we simply compute p+1 determinants.
	// This method replaces last column with the base vectors (0, ..., 0 , 1, 0, ... 0) 
	// to compute the coefficients, and so does not need to compute 
	// any eigen decomposition, just (p+1) determinants
	private TetradVector get_Stilde_polynomial_coefficients() {
		return null;
	}
	
	// generate a base vector of all zeros except for 1 in ith position
	private TetradVector get_base_vector(int n, int i) {
		TetradVector base_vec = new TetradVector(n);
		base_vec.assign(0);
		base_vec.set(0, 1);
		return base_vec;
	}
	
	// get the ith coefficient by computing determinant of appropriate matrix
	private double get_ith_coeff_of_Stilde_poly() {
		return Double.NaN;
	}
	
	// Step 6:Generate van der monde (VDM) matrix and solve the system VDM * pi_vec = b
	// generates the VDM matrix and solves the linear system. 
	// uses R's built in solve function - there may be a better VDM routine (as cited in Lindsay)
	private TetradVector generate_and_solve_VDM_system() {
		return null;
	}
	
	// simply takes the last column, and removes last element of last column
	private TetradVector get_VDM_b_vec(TetradMatrix mat) {
		return null;
	}
	
	// generates the van der monde matrix from a vector
	private TetradMatrix generate_van_der_monde(TetradVector vec) {
		return null;
	}
	
	// Step 7: Here we use mu_vec, pi_vec and lambdatilde_p to compute the composite pgamma values 
	//		 and combine them into the ifnal pvalue

	// get_mixed_p_val - weight sum of pgammas
	// now compute for a vector of quantiles - assume the vector of quantiles is very long,
	// while p < 10 (so vectorise over length of quantiles)
	private TetradVector get_mixed_p_val_vec() {
		return null;
	}
	
	// computes pgamma of the appropriate gamma function
	private double compute_composite_pgamma(int index, double qval, double shape_val, double[] scale_vec) {
		GammaDistribution gamma = new GammaDistribution(shape_val, scale_vec[index]);
		return gamma.cumulativeProbability(qval);
	}
	
	@Override
	public boolean isIndependent(Node x, Node y, Node... z) {
		List<Node> zList = Arrays.asList(z);
		return isIndependent(x, y, zList);
	}

	@Override
	public boolean isDependent(Node x, Node y, List<Node> z) {
		return !this.isIndependent(x, y, z);
	}

	@Override
	public boolean isDependent(Node x, Node y, Node... z) {
		List<Node> zList = Arrays.asList(z);
		return isDependent(x, y, zList);
	}

	@Override
	public double getPValue() {
		return 0;
	}

	@Override
	public List<Node> getVariables() {
		return null;
	}

	@Override
	public Node getVariable(String name) {
		return null;
	}

	@Override
	public List<String> getVariableNames() {
		return null;
	}

	@Override
	public boolean determines(List<Node> z, Node y) {
		return false;
	}

	@Override
	public double getAlpha() {
		return this.alpha;
	}

	@Override
	public void setAlpha(double alpha) {
		if (alpha < 0.0 || alpha > 1.0) {
			throw new IllegalArgumentException("Significance level must be in " + "[0, 1]: " + alpha);
		}

		this.alpha = alpha;
	}

	@Override
	public DataModel getData() {
		return (DataModel) dataSet;
	}

	@Override
	public ICovarianceMatrix getCov() {
		return null;
	}

	@Override
	public List<DataSet> getDataSets() {
		return null;
	}

	@Override
	public int getSampleSize() {
		return 0;
	}

	@Override
	public List<TetradMatrix> getCovMatrices() {
		return null;
	}

	@Override
	public double getScore() {
		return 0;
	}

	public DataSet getDataSet() {
		return dataSet;
	}

	public RandomIndApproximateMethod getApprox() {
		return approx;
	}

	public void setApprox(RandomIndApproximateMethod approx) {
		this.approx = approx;
	}

	public int getNum_feature() {
		return num_feature;
	}

	public void setNum_feature(int num_feature) {
		this.num_feature = num_feature;
	}

	private double getDistance(double[] x, double[] y) {
		double distance = 0;
		for (int i = 0; i < x.length; i++) {
			double diff = x[i] - y[i];
			distance += diff * diff;
		}
		return Math.sqrt(distance);
	}
	
}