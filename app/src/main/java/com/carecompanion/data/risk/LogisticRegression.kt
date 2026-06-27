package com.carecompanion.data.risk

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Minimal, dependency-free logistic-regression classifier with z-score feature
 * standardisation and L2-regularised batch gradient descent. Stays fully on-device
 * and remains interpretable (it yields a weight per named feature), which is why it is
 * the right first step up from the hand-tuned heuristic — not a black-box model.
 */
class LogisticRegression(
    private val learningRate: Double = 0.1,
    private val epochs: Int = 400,
    private val l2: Double = 0.001,
) {
    lateinit var mean: DoubleArray
        private set
    lateinit var std: DoubleArray
        private set
    lateinit var weights: DoubleArray
        private set
    var bias: Double = 0.0
        private set

    fun fit(x: List<DoubleArray>, y: IntArray) {
        val n = x.size
        require(n > 0) { "empty training set" }
        val d = x[0].size
        mean = DoubleArray(d) { j -> x.sumOf { it[j] } / n }
        std = DoubleArray(d) { j ->
            val v = x.sumOf { val e = it[j] - mean[j]; e * e } / n
            sqrt(v).let { if (it < 1e-8) 1.0 else it }
        }
        val xs = x.map { row -> DoubleArray(d) { (row[it] - mean[it]) / std[it] } }

        weights = DoubleArray(d)
        bias = 0.0
        repeat(epochs) {
            val gw = DoubleArray(d)
            var gb = 0.0
            for (i in 0 until n) {
                val p = sigmoid(dot(weights, xs[i]) + bias)
                val err = p - y[i]
                for (j in 0 until d) gw[j] += err * xs[i][j]
                gb += err
            }
            for (j in 0 until d) weights[j] -= learningRate * (gw[j] / n + l2 * weights[j])
            bias -= learningRate * (gb / n)
        }
    }

    /** Probability of the positive class (became IIT) for a raw, unstandardised row. */
    fun probability(row: DoubleArray): Double =
        sigmoid(bias + weights.indices.sumOf { weights[it] * ((row[it] - mean[it]) / std[it]) })

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z))
}
