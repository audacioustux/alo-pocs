// import java.io.PrintWriter
// import org.graalvm.polyglot.*
//
// object poly {
//   val polyCtx = Context
//     .newBuilder()
//     .allowAllAccess(true)
//     .option("engine.Mode", "throughput")
//     .build()
//   val jsSource = Source
//     .newBuilder(
//       "js",
//       "(1+Math.random()).toString()",
//       "dummymodule"
//     )
//     .build()
//   @main def bla() =
//     polyCtx.eval(jsSource)
// }
