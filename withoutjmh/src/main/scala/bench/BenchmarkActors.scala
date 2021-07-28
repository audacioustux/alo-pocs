/*
 * Copyright (C) 2014-2021 Lightbend Inc. <https://www.lightbend.com>
 */
package bench

object BenchmarkActors {
  def requireRightNumberOfCores(numCores: Int): Unit =
    require(
      Runtime.getRuntime.availableProcessors == numCores,
      s"Update the cores constant to ${Runtime.getRuntime.availableProcessors}"
    )
}
