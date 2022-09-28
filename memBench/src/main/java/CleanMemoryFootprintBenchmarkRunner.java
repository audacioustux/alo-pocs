import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.*;
import java.lang.ProcessBuilder.*;

public class CleanMemoryFootprintBenchmarkRunner {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (!args[0].equals("--warmup-iterations") || !args[2].equals("--result-iterations")) {
            System.err.println(args[0]);
            System.err.println("Usage: --warmup-iterations <n> --result-iterations <n>");
        }
        final int warmup_iterations = Integer.parseInt(args[1]);
        final int result_iterations = Integer.parseInt(args[3]);

        final String caseSpec = "test2.opt.wasm";

//        final Engine engine = Engine.newBuilder("wasm").build();
        final Context.Builder contextBuilder = Context.newBuilder("wasm");
//                .engine(engine);

        final List<Double> resultsEval = new ArrayList<>();
        final List<Double> resultsExec = new ArrayList<>();

        byte[] wasmBinary = Files.readAllBytes(Path.of("/Users/tanjimhossain/Bytes/poc-wormhole/memBench/src/main/wasm/" + caseSpec));
        ArrayList<Source> benchmarkSources = new ArrayList<>();
        benchmarkSources.add(Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "test2-0.wasm").build());
//        benchmarkSources.add(Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "test2-1.wasm").build());
//        benchmarkSources.add(Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "test2-2.wasm").build());

        for (int i = 0; i < warmup_iterations + result_iterations; ++i) {
            try (final Context context = contextBuilder
                    .allowAllAccess(true)
                    .option("wasm.Builtins", "wasi_snapshot_preview1")
                    .build()
            ) {
                final double heapSizeBeforeEval = getHeapSize();
                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "baseline")
                            .start()
                            .waitFor();
                benchmarkSources.forEach(context::eval);
                final double heapSizeAfterEval = getHeapSize();
                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "summary.diff")
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start()
                            .waitFor();
                final double resultEval = heapSizeAfterEval - heapSizeBeforeEval;

                if (i == warmup_iterations + result_iterations - 1)
                    new ProcessBuilder("jcmd", Long.toString(ProcessHandle.current().pid()), "VM.native_memory", "baseline")
                            .start()
                            .waitFor();
                List.of("main"
//                                , "test2-1.wasm"
//                        , "test2-2.wasm"
                        )
                        .forEach(member -> {
//                    Value memory = context.getBindings("wasm").getMember(member).getMember("memory");
//                    int stringLocation = context.getBindings("wasm").getMember(member).getMember("todos_create").execute().asInt();
                            context.getBindings("wasm").getMember(member).getMember("todos_create").execute().asInt();
//                    System.out.println(readStringFromMemory(memory, stringLocation, 49));
                        });
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
            if (i == warmup_iterations + result_iterations - 1) Thread.sleep(10000);
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

        sleep();
    }

//    private static String readStringFromMemory(Value memory, int location, int size) {
//        byte[] stringBytes = new byte[size];
//        for (int i = 0; i < stringBytes.length; i++) {
//            stringBytes[i] = memory.readBufferByte(location + i);
//        }
//        return new String(stringBytes);
//    }

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
