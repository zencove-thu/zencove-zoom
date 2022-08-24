// build.sc

import mill._, scalalib._
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`

trait CommonSpinalModule extends ScalaModule {
  override def scalaVersion = "2.12.15"

  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit"
  )

  override def ivyDeps = Agg(
    ivy"com.github.spinalhdl::spinalhdl-core:1.6.1",
    ivy"com.github.spinalhdl::spinalhdl-lib:1.6.1",
    ivy"com.github.scopt::scopt:4.0.1"
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"com.github.spinalhdl::spinalhdl-idsl-plugin:1.6.1",
    ivy"org.scalamacros:::paradise:2.1.1"
  )
}

object ZenCove extends CommonSpinalModule {
  override def mainClass = Some("zencove.soc.CPUTopVerilog")
}
