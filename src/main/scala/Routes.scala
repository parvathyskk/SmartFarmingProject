import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{StatusCodes, HttpMethods}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.mongodb.scala.bson.{BsonValue, BsonDouble, BsonInt32}
import org.mongodb.scala.Document
import SmartFarmingApp._
import akka.http.scaladsl.model.headers._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}
import io.circe.Json
import io.circe.parser._
import akka.util.Timeout
import scala.concurrent.duration._

object Routes {
  case class SensorData(
    soilMoisture: Double,
    temperature: Double,
    airHumidity: Double,
    ph: Double,
    rainfall: Double,
    n: Int,
    p: Int,
    k: Int
  )

  implicit val timeout: Timeout = Timeout(120.seconds)
  implicit val ec: ExecutionContext = ExecutionContext.global
  


  val corsResponseHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Headers`("Content-Type"),
    `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS)
  )

  val routes: Route = respondWithHeaders(corsResponseHeaders) {
    concat(
      pathSingleSlash {
        getFromFile("public/index.html")
      },
      getFromDirectory("public"),
      path("insert") {
        post {
          entity(as[SensorData]) { data =>
            SmartFarmingApp.insertReading(
              data.soilMoisture, data.temperature, data.airHumidity,
              data.ph, data.rainfall, data.n, data.p, data.k
            )
            complete(StatusCodes.OK, "Inserted successfully")
          }
        }
      },
      path("all") {
        get {
          complete(SmartFarmingApp.readAllDataAsList().map(_.toJson()))
        }
      },
      path("latest") {
        get {
          complete(
            SmartFarmingApp.getLatestReading()
              .map(_.toJson())
              .getOrElse(throw new NoSuchElementException("No data found"))
          )
        }
      },
      path("update") {
        put {
          parameters("timestamp", "field", "value") { (ts, field, valueStr) =>
            val bsonValue = field.toLowerCase match {
              case "soil_moisture" | "temperature" | "air_humidity" | "ph" | "rainfall" =>
                try Some(BsonDouble(valueStr.toDouble)) catch { case _: NumberFormatException => None }
              case "n" | "p" | "k" => 
                try Some(BsonInt32(valueStr.toInt)) catch { case _: NumberFormatException => None }
              case _ => None
            }

            complete {
              bsonValue match {
                case Some(v) if SmartFarmingApp.updateField(ts, field, v) =>
                  StatusCodes.OK -> s"Field '$field' updated."
                case Some(_) => 
                  StatusCodes.NotFound -> "Timestamp not found."
                case None =>
                  StatusCodes.BadRequest -> "Invalid field or value type."
              }
            }
          }
        }
      },
      path("delete") {
        delete {
          parameter("timestamp".as[String]) { ts =>
            complete {
              if (SmartFarmingApp.deleteByTimestamp(ts)) {
                StatusCodes.OK -> "Deleted successfully"
              } else {
                StatusCodes.NotFound -> "Timestamp not found"
              }
            }
          }
        }
      },
      pathPrefix("api") {
  path("predict_water") {
    withRequestTimeout(3.minutes) 
    get {
      extractRequest { request =>
        println(s"Starting prediction at ${java.time.Instant.now}")
        onComplete(SmartFarmingApp.predict_water()) {
          case Success(jsonStr) => 
            println(s"Prediction completed at ${java.time.Instant.now}")
            complete(jsonStr)
          case Failure(e) =>
            println(s"Prediction failed at ${java.time.Instant.now}: ${e.getMessage}")
            complete(StatusCodes.InternalServerError -> 
              Json.obj(
                "error" -> Json.fromString("Prediction timeout"),
                "details" -> Json.fromString("Operation took too long to complete")
              ))
        }
      }
    }
  }
}
    )
  }
}