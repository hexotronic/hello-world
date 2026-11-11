package io.github.hexotronic.lauscher.whisper

import java.io.File

/**
 * Chooses the fastest native build the CPU can run and a sensible thread count.
 * The "fast" library uses ARMv8.2 dot-product + FP16 instructions (most phones since 2018)
 * or AVX2/FMA/F16C on x86; older CPUs get the portable build.
 */
internal object NativeCpu {
    val supportsFast: Boolean by lazy {
        runCatching {
            val info = File("/proc/cpuinfo").readText().lowercase()
            val arch = System.getProperty("os.arch").orEmpty().lowercase()
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                // Every core must have the features (big.LITTLE CPUs list each core).
                val features = info.lines().filter { it.startsWith("features") }
                features.isNotEmpty() && features.all { it.contains("asimddp") && it.contains("asimdhp") }
            } else if (arch.contains("x86_64") || arch.contains("amd64")) {
                val flags = info.lines().filter { it.startsWith("flags") }
                flags.isNotEmpty() && flags.all { line -> listOf(" avx2", " fma", " f16c").all { line.contains(it) } }
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Loads "<name>_fast" when possible, otherwise "<name>". Returns the variant label. */
    fun load(name: String): String {
        if (supportsFast) {
            try {
                System.loadLibrary(name + "_fast")
                return "schnell"
            } catch (e: UnsatisfiedLinkError) {
                // Fall back to the portable build below.
            }
        }
        System.loadLibrary(name)
        return "standard"
    }

    /**
     * Number of performance cores. Using the slow efficiency cores as well makes ggml wait for
     * them: on a Dimensity 8200 (Xiaomi 13T) the four Cortex-A55 run at 2.0 GHz, two thirds of the
     * A78's 3.1 GHz, but do far less per clock. So the kernel's relative core capacity decides where
     * it is known (big cores have at least half of the largest), otherwise the clock (at least 80 %).
     */
    val performanceCores: Int by lazy {
        runCatching {
            val cpus = 0 until Runtime.getRuntime().availableProcessors()
            fun read(cpu: Int, name: String) = File("/sys/devices/system/cpu/cpu$cpu/$name").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
            val capacities = cpus.mapNotNull { read(it, "cpu_capacity") }
            val freqs = cpus.mapNotNull { read(it, "cpufreq/cpuinfo_max_freq") }
            fastCores(capacities.takeIf { it.size == cpus.count() }, freqs) ?: error("no cpu information")
        }.getOrElse { Runtime.getRuntime().availableProcessors() / 2 }.coerceIn(2, 8)
    }

    /** The cores with at least half of the largest capacity, or else at least 80 % of the highest clock. */
    fun fastCores(capacities: List<Long>?, maxFreqs: List<Long>): Int? = when {
        !capacities.isNullOrEmpty() && capacities.max() > 0 && capacities.distinct().size > 1 -> capacities.count { it * 2 >= capacities.max() }
        maxFreqs.isNotEmpty() -> maxFreqs.count { it * 5 >= maxFreqs.max() * 4 }
        else -> null
    }
}
