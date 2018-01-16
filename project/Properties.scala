object Properties {
  val dynamicLinker = Option(System.getProperty("build.dynamicLinker"))
  val memory = Option(System.getProperty("build.mx")).getOrElse("2048")
  val nativeMode = System.getProperty("build.nativeMode", "debug")
}
