package com.carecompanion.biometric.secugen

/** Thrown when the SecuGen SGFPLIB SDK returns a non-zero error code */
class SecuGenException(
    val errorCode: Long,
    override val message: String = SecuGenError.describe(errorCode.toInt()),
    cause: Throwable? = null
) : Exception(message, cause)

/** Thrown when no supported SecuGen device is found over USB */
class NoScannerException(message: String = "No SecuGen USB scanner detected") : Exception(message)

/** Thrown when capture quality is below threshold after all retries */
class PoorQualityException(
    val quality: Int,
    val threshold: Int,
    val attempts: Int
) : Exception("Image quality $quality < threshold $threshold after $attempts attempts")