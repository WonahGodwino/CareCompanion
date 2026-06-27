package com.carecompanion.data.network.models

import com.google.gson.annotations.SerializedName

/** The federated-learning "knowledge packet" served by WINCO `/api/knowledge/model/current`. */
data class WincoModelPacket(
    @SerializedName("schema_version") val schemaVersion: String,
    @SerializedName("feature_names")  val featureNames: List<String>,
    @SerializedName("weights")        val weights: List<Double>,
    @SerializedName("bias")           val bias: Double,
    @SerializedName("scaling")        val scaling: WincoModelScaling,
    @SerializedName("training")       val training: WincoModelTraining,
)

data class WincoModelScaling(
    @SerializedName("mean") val mean: List<Double>,
    @SerializedName("std")  val std: List<Double>,
)

data class WincoModelTraining(
    @SerializedName("n_samples")     val nSamples: Int = 0,
    @SerializedName("auc")           val auc: Double? = null,
    @SerializedName("heuristic_auc") val heuristicAuc: Double? = null,
)
