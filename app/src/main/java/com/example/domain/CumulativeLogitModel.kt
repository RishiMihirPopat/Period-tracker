package com.example.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Generic K-level ordinal cumulative-logit regression solver with Newton-Raphson / IRLS.
 * For K=2, this reduces exactly to binary logistic regression.
 *
 * Implements strict numerical guards:
 * - L2 ridge penalty on slope and intercepts
 * - Positive-definite Hessian check via Cholesky decomposition
 * - 25-iteration cap
 * - Step-halving line search on deviance increase
 * - Near-perfect separation guard (|beta| > 15)
 */
class CumulativeLogitModel(
    val numCategories: Int, // K >= 2
    private val l2SlopePenalty: Double = 0.5,
    private val l2InterceptPenalty: Double = 0.01,
    private val maxIterations: Int = 25
) {
    init {
        require(numCategories >= 2) { "Number of categories K must be >= 2" }
    }

    data class FitResult(
        val cutpoints: DoubleArray, // length K - 1
        val beta: Double,           // slope for predictor x
        val converged: Boolean
    ) {
        /**
         * Computes predicted probability distribution over the K categories for a given x.
         */
        fun predictProbabilities(x: Double): DoubleArray {
            val k = cutpoints.size + 1
            val gammas = DoubleArray(cutpoints.size) { i ->
                val eta = cutpoints[i] - beta * x
                sigmoid(eta)
            }
            val probs = DoubleArray(k)
            probs[0] = gammas[0]
            for (i in 1 until cutpoints.size) {
                probs[i] = (gammas[i] - gammas[i - 1]).coerceAtLeast(0.0)
            }
            probs[k - 1] = (1.0 - gammas.last()).coerceAtLeast(0.0)

            // Normalize
            val sum = probs.sum()
            if (sum > 0.0) {
                for (i in probs.indices) {
                    probs[i] /= sum
                }
            }
            return probs
        }
    }

    /**
     * Fits the model to observations (x_i, y_i), where y_i in 0 until numCategories.
     * Returns null if non-convergent, separated, or insufficient data.
     */
    fun fit(data: List<Pair<Double, Int>>): FitResult? {
        if (data.size < 10) return null

        val counts = IntArray(numCategories)
        for ((_, y) in data) {
            if (y !in 0 until numCategories) return null
            counts[y]++
        }
        // If outcome has zero variance (all in one category), cannot fit slope
        val nonZeroClasses = counts.count { it > 0 }
        if (nonZeroClasses < 2) return null

        val m = numCategories - 1 // number of cutpoints
        val p = m + 1             // cutpoints + beta

        // Initial cutpoint estimates based on empirical cumulative log-odds
        val omega = DoubleArray(p)
        var cumulativeCount = 0
        for (j in 0 until m) {
            cumulativeCount += counts[j]
            val pCum = (cumulativeCount.toDouble() + 0.5) / (data.size.toDouble() + 1.0)
            omega[j] = logit(pCum.coerceIn(0.001, 0.999))
        }
        // Ensure initial cutpoints are strictly increasing
        for (j in 1 until m) {
            if (omega[j] <= omega[j - 1]) {
                omega[j] = omega[j - 1] + 0.2
            }
        }
        omega[m] = 0.0 // initial beta = 0

        var currentDeviance = computeDeviance(omega, data)

        for (iter in 0 until maxIterations) {
            val gradient = DoubleArray(p)
            val infoMatrix = Array(p) { DoubleArray(p) }

            // Accumulate score vector and expected Fisher information
            for ((x, y) in data) {
                accumulateObservation(omega, x, y, gradient, infoMatrix)
            }

            // Apply L2 ridge penalty to gradient
            for (j in 0 until m) {
                gradient[j] -= l2InterceptPenalty * omega[j]
            }
            gradient[m] -= l2SlopePenalty * omega[m]

            // Apply L2 ridge penalty to information matrix diagonal
            for (j in 0 until m) {
                infoMatrix[j][j] += l2InterceptPenalty
            }
            infoMatrix[m][m] += l2SlopePenalty

            // Cholesky decomposition of infoMatrix (Hessian check)
            val chol = cholesky(infoMatrix) ?: return null // Non-positive definite -> abort

            val delta = chol.solve(gradient)

            // Guard against extreme step / separation
            var maxDelta = 0.0
            for (d in delta) {
                val absD = abs(d)
                if (absD > maxDelta) maxDelta = absD
            }
            if (maxDelta > 20.0 || delta.any { it.isNaN() || it.isInfinite() }) {
                return null
            }

            // Step-halving line search
            var stepFraction = 1.0
            var accepted = false
            val candidateOmega = DoubleArray(p)

            for (halving in 0..5) {
                for (j in 0 until p) {
                    candidateOmega[j] = omega[j] + stepFraction * delta[j]
                }

                // Check cutpoint ordering: theta_0 < theta_1 < ... < theta_{m-1}
                var ordered = true
                for (j in 1 until m) {
                    if (candidateOmega[j] <= candidateOmega[j - 1]) {
                        ordered = false
                        break
                    }
                }

                if (ordered) {
                    val newDeviance = computeDeviance(candidateOmega, data)
                    if (newDeviance <= currentDeviance + 1e-6) {
                        for (j in 0 until p) {
                            omega[j] = candidateOmega[j]
                        }
                        currentDeviance = newDeviance
                        accepted = true
                        break
                    }
                }
                stepFraction *= 0.5
            }

            // Check convergence
            val beta = omega[m]
            if (abs(beta) > 15.0) {
                // Near-perfect separation guard
                return null
            }

            if (maxDelta < 1e-4 || (!accepted && stepFraction < 0.05)) {
                return FitResult(
                    cutpoints = omega.copyOfRange(0, m),
                    beta = beta,
                    converged = true
                )
            }
        }

        // Hit iteration cap without clean convergence
        return null
    }

    private fun accumulateObservation(
        omega: DoubleArray,
        x: Double,
        y: Int,
        gradient: DoubleArray,
        infoMatrix: Array<DoubleArray>
    ) {
        val m = numCategories - 1
        val beta = omega[m]

        val gammas = DoubleArray(m)
        val densities = DoubleArray(m)
        for (j in 0 until m) {
            val eta = omega[j] - beta * x
            val g = sigmoid(eta)
            gammas[j] = g
            densities[j] = g * (1.0 - g)
        }

        val probs = DoubleArray(numCategories)
        probs[0] = gammas[0]
        for (j in 1 until m) {
            probs[j] = max(1e-12, gammas[j] - gammas[j - 1])
        }
        probs[m] = max(1e-12, 1.0 - gammas.last())

        // Compute score vector for actual observed category y
        val sObs = computeScoreForCategory(y, x, gammas, densities, probs)
        for (j in 0 until (m + 1)) {
            gradient[j] += sObs[j]
        }

        // Expected Fisher Information = sum_{k=0}^{K-1} pi_k * s_k * s_k^T
        for (k in 0 until numCategories) {
            val pi_k = probs[k]
            if (pi_k > 1e-12) {
                val s_k = computeScoreForCategory(k, x, gammas, densities, probs)
                for (r in 0 until (m + 1)) {
                    val s_r = s_k[r]
                    for (c in r until (m + 1)) {
                        val contrib = pi_k * s_r * s_k[c]
                        infoMatrix[r][c] += contrib
                        if (r != c) {
                            infoMatrix[c][r] += contrib
                        }
                    }
                }
            }
        }
    }

    private fun computeScoreForCategory(
        k: Int,
        x: Double,
        gammas: DoubleArray,
        densities: DoubleArray,
        probs: DoubleArray
    ): DoubleArray {
        val m = numCategories - 1
        val score = DoubleArray(m + 1)
        val p_k = max(1e-12, probs[k])

        // d/d theta_j
        if (k < m) {
            score[k] += densities[k] / p_k
        }
        if (k > 0) {
            score[k - 1] -= densities[k - 1] / p_k
        }

        // d/d beta
        val g_curr = if (k < m) densities[k] else 0.0
        val g_prev = if (k > 0) densities[k - 1] else 0.0
        score[m] = -x * (g_curr - g_prev) / p_k

        return score
    }

    private fun computeDeviance(omega: DoubleArray, data: List<Pair<Double, Int>>): Double {
        val m = numCategories - 1
        val beta = omega[m]
        var logLik = 0.0

        for ((x, y) in data) {
            val gammas = DoubleArray(m) { j -> sigmoid(omega[j] - beta * x) }
            val prob = when (y) {
                0 -> gammas[0]
                m -> 1.0 - gammas.last()
                else -> gammas[y] - gammas[y - 1]
            }
            logLik += ln(max(1e-12, prob))
        }

        // Add L2 penalty to deviance (-2 * penalizedLogLik)
        var pen = l2SlopePenalty * beta * beta
        for (j in 0 until m) {
            pen += l2InterceptPenalty * omega[j] * omega[j]
        }
        return -2.0 * (logLik - 0.5 * pen)
    }

    private class CholeskyResult(val l: Array<DoubleArray>) {
        val n = l.size

        fun solve(b: DoubleArray): DoubleArray {
            val y = DoubleArray(n)
            // Forward solve L y = b
            for (i in 0 until n) {
                var sum = b[i]
                for (k in 0 until i) {
                    sum -= l[i][k] * y[k]
                }
                y[i] = sum / l[i][i]
            }
            // Backward solve L^T x = y
            val x = DoubleArray(n)
            for (i in n - 1 downTo 0) {
                var sum = y[i]
                for (k in (i + 1) until n) {
                    sum -= l[k][i] * x[k]
                }
                x[i] = sum / l[i][i]
            }
            return x
        }
    }

    private fun cholesky(a: Array<DoubleArray>): CholeskyResult? {
        val n = a.size
        val l = Array(n) { DoubleArray(n) }

        for (i in 0 until n) {
            for (j in 0..i) {
                var sum = a[i][j]
                for (k in 0 until j) {
                    sum -= l[i][k] * l[j][k]
                }
                if (i == j) {
                    if (sum <= 1e-8) {
                        return null // Not positive definite
                    }
                    l[i][j] = sqrt(sum)
                } else {
                    l[i][j] = sum / l[j][j]
                }
            }
        }
        return CholeskyResult(l)
    }

    companion object {
        private fun sigmoid(z: Double): Double {
            return when {
                z > 35.0 -> 1.0
                z < -35.0 -> 0.0
                else -> 1.0 / (1.0 + exp(-z))
            }
        }

        private fun logit(p: Double): Double {
            val clamped = p.coerceIn(1e-7, 1.0 - 1e-7)
            return ln(clamped / (1.0 - clamped))
        }
    }
}
