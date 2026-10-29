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
     * Number of performance cores: cores whose maximum clock is close to the fastest one.
     * Using the slow efficiency cores as well makes ggml wait for them.
     */
    val performanceCores: Int by lazy {
        runCatching {
            val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { cpu ->
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
            }
            if (freqs.isEmpty()) error("no cpufreq")
            val max = freqs.max()
            freqs.count { it >= max * 0.7 }
        }.getOrElse { Runtime.getRuntime().availableProcessors() / 2 }.coerceIn(2, 8)
    }
}
