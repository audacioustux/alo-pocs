import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

class WasmModule {
    private Context ctx;

    public WasmModule(Engine engine, Source wasmSource) throws IOException {
        String[] args = { "warmup", "true" };
        ctx = Context.newBuilder().engine(engine).arguments("wasm", args)
                .option("wasm.Builtins", "wasi_snapshot_preview1").allowAllAccess(true).build();
        ctx.eval(wasmSource);
    }

    public Value exec() {
        return ctx.getBindings("wasm").getMember("main").getMember("_start");
    }
}

class PolyglotEval {
    PolyglotEval(int n) throws IOException, InterruptedException {
        TimeDiff polyCtx_timer = new TimeDiff("Engine, Source init", TimeDiff.DiffType.US);
        // Context polyCtx = Context.newBuilder().option("python.CoreHome",
        // "/Users/tanjimhossain/.asdf/installs/java/graalvm-ee-java11-21.1.0/Contents/Home/languages/python/lib-graalpython")
        // .option("python.StdLibHome",
        // "/Users/tanjimhossain/.asdf/installs/java/graalvm-ee-java11-21.1.0/Contents/Home/languages/python/lib-python/3")
        // .allowAllAccess(true).build();
        //
        byte[] wasmBinary = Files
                // .readAllBytes(Path.of("../fromscratch1/src/main/rust/target/wasm32-wasi/release/rust.opt.wasm"));
                .readAllBytes(Path.of(
                        "/Users/tanjimhossain/Bytes/rubiks-code/AOC/Rust/2020/target/wasm32-wasi/release/day_11.wasm"));
        // .readAllBytes(Path.of("../fromscratch1/src/main/rust/"));
        Source wasmSource = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "test.wasm").build();

        Engine engine = Engine.newBuilder().option("engine.Mode", "throughput").build();

        // Context polyCtx = Context.newBuilder().engine(engine).option("engine.Mode",
        // "throughput")
        // .option("wasm.Builtins",
        // "wasi_snapshot_preview1").allowAllAccess(true).build();

        polyCtx_timer.stop();

        // TimeDiff eval_all_nothing_timer = new TimeDiff("all lang eval nothing
        // total:", TimeDiff.DiffType.US);
        // polyCtx.eval("js", "");
        // polyCtx.eval("R", "");
        // polyCtx.eval("ruby", "");
        // polyCtx.eval("python", "");
        // eval_all_nothing_timer.stop();
        // Thread.sleep(10000);
        // TimeDiff warmup_exec_total_timer = new TimeDiff("warmup lang exec total",
        // TimeDiff.DiffType.US);
        // for (int i = 0; i < 1000; i++) {
        // wasmModule.init(engine);
        // wasmModule.exec();
        // }
        // warmup_exec_total_timer.stop();

        TimeDiff module_eval_total_timer = new TimeDiff("module eval total", TimeDiff.DiffType.US);
        ArrayList<Value> wasmModuleFns = new ArrayList<Value>();
        for (int i = 0; i < n; i++) {
            Value fn = (new WasmModule(engine, wasmSource)).exec();
            wasmModuleFns.add(fn);
        }
        module_eval_total_timer.stop();

        TimeDiff warmup_exec_total_timer = new TimeDiff("warmup lang exec total", TimeDiff.DiffType.US);

        for (Value fn : wasmModuleFns) {
            for (int j = 0; j < 1; j++) {
                fn.execute();
            }
        }
        warmup_exec_total_timer.stop();
        //
        // Thread.sleep(5000);
        // TimeDiff exec_total_timer = new TimeDiff("lang exec total",
        // TimeDiff.DiffType.US);
        // for (Value fn : wasmModuleFns) {
        // TimeDiff exec_timer = new TimeDiff("lang exec", TimeDiff.DiffType.US);
        // for (int j = 0; j < 100000; j++) {
        // fn.execute();
        // }
        // exec_timer.stop();
        // }
        // exec_total_timer.stop();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Context context = Context.newBuilder().allowIO(true).build();
        // Value array = context.eval("python", "[1,2,42,4]");
        // int result = array.getArrayElement(2).asInt();
        // System.out.println(result);
        final var n = (args.length != 0) ? Integer.parseInt(args[0]) : 1;
        new PolyglotEval(n);
    }

    // private static void jsModuleEval(Context ctx) {
    // String jsSrc = "import { Test } from
    // '/Users/tanjimhossain/IdeaProjects/graalbench/src/main/js/test.mjs';"
    // + "const test = new Test();" + "test.hello();";
    // try {
    // ctx.eval(Source.newBuilder("js", "", "nothing.mjs").build());
    //
    // TimeDiff poly_exec_timer = new TimeDiff("js module eval",
    // TimeDiff.DiffType.NS);
    // Value array = ctx.eval(Source.newBuilder("js", jsSrc, "test.mjs").build());
    // int result = array.getArrayElement(2).asInt();
    // poly_exec_timer.stop();
    // System.out.println(result);
    // } catch (IOException e) {
    // }
    // }

    // private static void rEval(Context ctx) {
    // TimeDiff eval_timer = new TimeDiff("R eval", TimeDiff.DiffType.NS);
    // Value array = ctx.eval("R", "c(1,2,42,4)");
    // int result = array.getArrayElement(2).asInt();
    // eval_timer.stop();
    // System.out.println(result);
    // }

    // private static void jsEval(Context ctx) {
    // TimeDiff eval_timer = new TimeDiff("JS eval", TimeDiff.DiffType.NS);
    // Value array = ctx.eval("js", "[1,2,42,4]");
    // int result = array.getArrayElement(2).asInt();
    // eval_timer.stop();
    // System.out.println(result);
    // }

    // private static void rubyEval(Context ctx) {
    // TimeDiff eval_timer = new TimeDiff("Ruby eval", TimeDiff.DiffType.NS);
    // Value array = ctx.eval("ruby", "[1,2,42,4]");
    // int result = array.getArrayElement(2).asInt();
    // eval_timer.stop();
    // System.out.println(result);
    // }

    // private static void pythonEval(Context ctx) {
    // TimeDiff eval_timer = new TimeDiff("python eval", TimeDiff.DiffType.NS);
    // Value array = ctx.eval("python", "[1,2,42,4]");
    // int result = array.getArrayElement(2).asInt();
    // eval_timer.stop();
    // System.out.println(result);
    // }
}
