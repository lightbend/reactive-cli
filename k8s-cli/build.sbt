import sbt._


// Disable GC since the CLI is a short-lived process.
nativeGC := "none"

nativeLinkingOptions := Seq(
  "-lcurl",
  "-L", (baseDirectory.value / ".." / "libhttpsimple" / "target").toPath.toAbsolutePath.toString
) ++ sys.props.get("nativeLinkingOptions").fold(Seq.empty[String])(_.split(" "))

