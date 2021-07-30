// import org.graalvm.polyglot.*;

public class graalisolatehello {
    public static int fib(int x) {
        if (x < 2) {
            return 1;
        } else {
            return fib(x - 1) + fib(x - 2);
        }
    }

    public static void main(String[] args) throws InterruptedException {

        System.out.println(fib(40));
        Thread.sleep(5000);
        // Thread.sleep(10000);
        // Engine polyglotEngine = Engine.newBuilder().build();
        // for (int i = 1; i < 300000; i++) {
        // System.out.println(i);
        // Context ctx = Context.newBuilder().engine(polyglotEngine).build();
        // // System.out.println(ctx.eval("js", "1").asInt());
        // ctx.eval("js", "");
        // ctx.close();
        // }
        // System.gc();
        // polyglotEngine.close();
    }
}
