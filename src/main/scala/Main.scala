import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Main extends App {
  implicit val system: ActorSystem = ActorSystem("smart-farming-system")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(Routes.routes)

  println("Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // Wait for user to press return
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
