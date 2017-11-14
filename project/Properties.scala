object Properties {
  val concurrentBuilds = Option(System.getProperty("build.concurrentBuilds")).map(_.toInt).getOrElse(2)
  val dynamicLinker = Option(System.getProperty("build.dynamicLinker"))
  val nativeMode = System.getProperty("build.nativeMode", "debug")
}
