import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class MemoryFootprintBenchmarkRunner {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (!args[0].equals("--warmup-iterations") || !args[2].equals("--result-iterations")) {
            System.err.println(args[0]);
            System.err.println("Usage: --warmup-iterations <n> --result-iterations <n>");
        }

        final int warmup_iterations = Integer.parseInt(args[1]);
        final int result_iterations = Integer.parseInt(args[3]);

        final String caseSpec = "test2.mjs";

//        final Engine engine = Engine.newBuilder("js").build();
//        final Engine engine = Engine.newBuilder("wasm").build();
//        final Context.Builder contextBuilder = Context.newBuilder("wasm").engine(engine);
        final Context.Builder contextBuilder = Context.newBuilder("js")
//                .engine(engine)
                .option("js.esm-eval-returns-exports", "true");

        final List<Double> resultsEval = new ArrayList<>();
        final List<Double> resultsExec = new ArrayList<>();

//            byte[] wasmBinary = Files.readAllBytes(Path.of("/Users/tanjimhossain/Bytes/poc-wormhole/memBench/src/main/wasm/" + caseSpec));
        ArrayList<Source> benchmarkSources = new ArrayList<>();
//            benchmarkSources.add(Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "test2.wasm").build());
        benchmarkSources.add(Source
                .newBuilder(
                        "js",
                        "export { todos_create as _main } from '/Users/tanjimhossain/Bytes/poc-wormhole/memBench/src/main/js/test2.mjs';",
                        "test2-0.mjs"
                ).mimeType("application/javascript+module").build());
//        benchmarkSources.add(Source
//                .newBuilder(
//                        "js",
//                        "export { todos_create as _main } from '/Users/tanjimhossain/Bytes/poc-wormhole/memBench/src/main/js/test2-1.mjs';",
//                        "test2-1.mjs"
//                ).mimeType("application/javascript+module").build());
//        benchmarkSources.add(Source
//                .newBuilder(
//                        "js",
//                        "export { todos_create as _main } from '/Users/tanjimhossain/Bytes/poc-wormhole/memBench/src/main/js/test2-2.mjs';",
//                        "test2-2.mjs"
//                ).mimeType("application/javascript+module").build());

        for (int i = 0; i < warmup_iterations + result_iterations; ++i) {
            try (final Context context = contextBuilder
                    .allowAllAccess(true)
                    .build()
            ) {
                final double heapSizeBeforeEval = getHeapSize();
                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "baseline")
                            .start()
                            .waitFor();
                final List<Value> vals = benchmarkSources.stream().map(context::eval).toList();
                final double heapSizeAfterEval = getHeapSize();
                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "summary.diff")
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start()
                            .waitFor();
                final double resultEval = heapSizeAfterEval - heapSizeBeforeEval;

//                context.getBindings("wasm").getMember("main").getMember("todos_create").execute();
//                vals.forEach(value -> System.out.println(value.getMember("_main").execute().asString()));
                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "baseline")
                            .start()
                            .waitFor();
                vals.forEach(value -> value.getMember("_main").execute().asString());
                final double heapSizeAfterExec = getHeapSize();
                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "summary.diff")
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start()
                            .waitFor();
                final double resultExec = heapSizeAfterExec - heapSizeAfterEval;

                if (i < warmup_iterations) {
                    System.out.format("%s: warmup iteration[%d]: %.3f MB, %.3f MB%n", caseSpec, i, resultEval,
                            resultExec);
                } else {
                    resultsEval.add(resultEval);
                    resultsExec.add(resultExec);
                    System.out.format("%s: iteration[%d]: %.3f MB, %.3f MB%n", caseSpec, i, resultEval, resultExec);
                }
            }
        }

        Collections.sort(resultsEval);

        System.out.format("%s: median: %.3f MB%n", caseSpec, median(resultsEval));
        System.out.format("%s: min: %.3f MB%n", caseSpec, resultsEval.get(0));
        System.out.format("%s: max: %.3f MB%n", caseSpec, resultsEval.get(resultsEval.size() - 1));
        System.out.format("%s: average: %.3f MB%n", caseSpec, average(resultsEval));

        Collections.sort(resultsExec);

        System.out.format("%s: median: %.3f MB%n", caseSpec, median(resultsExec));
        System.out.format("%s: min: %.3f MB%n", caseSpec, resultsExec.get(0));
        System.out.format("%s: max: %.3f MB%n", caseSpec, resultsExec.get(resultsExec.size() - 1));
        System.out.format("%s: average: %.3f MB%n", caseSpec, average(resultsExec));

    }

    static double getHeapSize() {
        sleep();
        System.gc();
        sleep();
        final Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1000000.0;
    }

    static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static double median(List<Double> xs) {
        final int size = xs.size();
        if (size % 2 == 0) {
            return (xs.get(size / 2) + xs.get(size / 2 - 1)) / 2.0;
        } else {
            return xs.get(size / 2);
        }
    }

    private static double average(List<Double> xs) {
        double result = 0.0;
        for (double x : xs) {
            result += x;
        }
        return result / xs.size();
    }
}