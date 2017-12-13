object Properties {
  val dynamicLinker = Option(System.getProperty("build.dynamicLinker"))
  val nativeMode = System.getProperty("build.nativeMode", "debug")
}
