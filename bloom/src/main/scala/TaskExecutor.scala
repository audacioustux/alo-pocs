import org.graalvm.polyglot._

@main def testMultiple(): Unit =
  val polyglotCtx = Context.create()
  val eval1 = polyglotCtx.eval(Source.newBuilder("js", "() => 'hello 1'", "test1.mjs").build())
  val eval2 = polyglotCtx.eval(Source.newBuilder("js", "() => 'hello 2'", "test2.mjs").build())

  println(eval1.execute())
  println(eval2.execute())

class TaskExecutor {

  import TaskExecutor._

  private final val threadLocalCtx: ThreadLocal[Context] = ThreadLocal.withInitial(() =>
    Context.newBuilder()
      .option("wasm.Builtins", "wasi_snapshot_preview1")
      .engine(engine)
      .build())

  def eval(source: Source): Object =
    ???

}

object TaskExecutor {
  private final val engine = Engine.create("wasm")
}
