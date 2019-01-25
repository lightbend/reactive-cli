import sbt._
import scala.sys.process._

object AdditionalIO {
  def setExecutable(file: File): Unit =
    assert(file.setExecutable(true), s"Marking $file executable failed")

  def runProcess(args: String*): Unit = {
    val code = args.!
    assert(code == 0, s"Executing $args yielded $code, expected 0")
  }

  def runProcessCwd(cwd: File, args: String*): Unit = {
    val code = Process(args, cwd).!
    assert(code == 0, s"Executing $args yielded $code, expected 0")
  }
}
