package controllers

import play.api.mvc.Controller
import play.api.mvc.Result
import scala.concurrent.ExecutionContext
import java.io.File
import play.api.Logger
import org.apache.lucene.search.TimeLimitingCollector
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import java.util.concurrent.ConcurrentHashMap
import java.io.PrintWriter
import scala.concurrent.Future
import javax.inject.Inject
import akka.stream.Materializer
import java.io.StringWriter
import play.api.Environment

abstract class QueuingController(materializer: Materializer, env: Environment) extends Controller {
  
  private lazy val tmpDir = {
    val tmpDir = env.getFile("tmp")
    tmpDir.mkdir()
    tmpDir.getPath
  }
  
  private def getStackTraceAsString(t: Throwable) = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
  
  val sha1md = java.security.MessageDigest.getInstance("SHA-1")
  
  val processing = new ConcurrentHashMap[String,Future[Result]]
  
  private def writeFile(file: File, content: String) {
    val pw = new PrintWriter(file)
    pw.write(content)
    pw.close()
  }

  protected def getOrCreateResult(callId: String, force: Boolean, call: () => Result)(implicit ec: ExecutionContext): Result = {
    Logger.info(callId)
    val name = play.api.libs.Codecs.sha1(sha1md.digest(callId.getBytes))
    val tf = new File(tmpDir+"/result-"+name+".json")
    if (force) tf.delete()
    if (tf.createNewFile()) {
      val tf2 = new File(tmpDir+"/result-"+name+".parameters")
      writeFile(tf2, callId)
      val future = Future { call() } .flatMap(_.body.consumeData(materializer).map(c => c.decodeString("UTF-8"))).map(content => {
        writeFile(tf, content)
        processing.remove(name)
        Ok(content).as(JSON)
      }).recover{ case cause =>
        Logger.error("Error processing "+callId+": "+getStackTraceAsString(cause))
        tf.delete()
        processing.remove(name)
        if (cause.isInstanceOf[TimeLimitingCollector.TimeExceededException]) {
          val tlcause = cause.asInstanceOf[TimeLimitingCollector.TimeExceededException]
          BadRequest(s"Query timeout ${tlcause.getTimeAllowed/1000}s exceeded. If you want this to succeed, increase the timeout parameter.")
        } else throw cause
      }
      processing.put(name, future)
    } else Logger.info("Reusing ready result for "+callId)
    val f = new File(tmpDir+"/result-"+name+".json")
    if (!f.exists()) InternalServerError("\"An error has occurred, please try again.\"")
    else Option(processing.get(name)).map(Await.result(_, Duration.Inf)).getOrElse(Ok.sendFile(f).as(JSON))
  }

}