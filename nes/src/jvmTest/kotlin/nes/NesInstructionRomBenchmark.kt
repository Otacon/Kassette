package nes

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
class NesInstructionBenchmark {

    @Benchmark
    fun allInstructions() {
        val console = createConsole(resourceName = "/all_instrs.nes")

        runBlarggTest(console = console, maxFrames = 20_000)
    }
}
