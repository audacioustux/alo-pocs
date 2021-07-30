import java.io.IOException;

import org.graalvm.polyglot.*;

class PolyglotEval {
    PolyglotEval(int n) {
        TimeDiff polyCtx_timer = new TimeDiff("poly context launch", TimeDiff.DiffType.NS);
        // Context polyCtx = Context.newBuilder().option("python.CoreHome",
        // "/Users/tanjimhossain/.asdf/installs/java/graalvm-ee-java11-21.1.0/Contents/Home/languages/python/lib-graalpython")
        // .option("python.StdLibHome",
        // "/Users/tanjimhossain/.asdf/installs/java/graalvm-ee-java11-21.1.0/Contents/Home/languages/python/lib-python/3")
        // .allowAllAccess(true).build();
        Context polyCtx = Context.newBuilder().option("engine.Mode", "throughput").allowAllAccess(true).build();
        polyCtx_timer.stop();

        TimeDiff eval_all_nothing_timer = new TimeDiff("all lang eval nothing total:", TimeDiff.DiffType.US);
        polyCtx.eval("js", "");
        // polyCtx.eval("R", "");
        // polyCtx.eval("ruby", "");
        // polyCtx.eval("python", "");
        eval_all_nothing_timer.stop();

        for (int i = 0; i < n; i++) {
            TimeDiff eval_all_timer = new TimeDiff("all lang eval total:", TimeDiff.DiffType.US);
            System.out.println("iteration: " + i);
            // rEval(polyCtx);
            jsEval(polyCtx);
            // rubyEval(polyCtx);
            // pythonEval(polyCtx);
            jsModuleEval(polyCtx);
            eval_all_timer.stop();
        }
    }

    PolyglotEval() {
        new PolyglotEval(1);
    }

    public static void main(String[] args) {
        final var n = (args.length != 0) ? Integer.parseInt(args[0]) : 10;
        new PolyglotEval(n);
    }

    private static void jsModuleEval(Context ctx) {
        String jsSrc = "import { Test } from '/Users/tanjimhossain/IdeaProjects/graalbench/src/main/js/test.mjs';"
                + "const test = new Test();" + "test.hello();";
        try {
            ctx.eval(Source.newBuilder("js", "", "nothing.mjs").build());

            TimeDiff poly_exec_timer = new TimeDiff("js module eval", TimeDiff.DiffType.NS);
            Value array = ctx.eval(Source.newBuilder("js", jsSrc, "test.mjs").build());
            int result = array.getArrayElement(2).asInt();
            poly_exec_timer.stop();
            System.out.println(result);
        } catch (IOException e) {
        }
    }

    // private static void rEval(Context ctx) {
    // TimeDiff eval_timer = new TimeDiff("R eval", TimeDiff.DiffType.NS);
    // Value array = ctx.eval("R", "c(1,2,42,4)");
    // int result = array.getArrayElement(2).asInt();
    // eval_timer.stop();
    // System.out.println(result);
    // }

    private static void jsEval(Context ctx) {
        TimeDiff eval_timer = new TimeDiff("JS eval", TimeDiff.DiffType.NS);
        Value array = ctx.eval("js", "[1,2,42,4]");
        int result = array.getArrayElement(2).asInt();
        eval_timer.stop();
        System.out.println(result);
    }

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
