import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpMethods._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.mongodb.scala.bson.{BsonValue, BsonDouble, BsonInt32}
import org.mongodb.scala.Document
import SmartFarmingApp._
import akka.http.scaladsl.model.headers._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Directives.onComplete
import scala.util.{Success, Failure}
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._

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

  val corsResponseHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Headers`("Content-Type"),
    `Access-Control-Allow-Methods`(GET, POST, OPTIONS)
  )

  val routes: Route =
    respondWithHeaders(corsResponseHeaders) {
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
            val docs = SmartFarmingApp.readAllDataAsList()
            complete(docs.map(_.toJson()))
          }
        },
        
        path("latest") {
          get {
            SmartFarmingApp.getLatestReading() match {
              case Some(doc) => complete(doc.toJson())
              case None => complete(StatusCodes.NotFound -> "No data found")
            }
          }
        },
        
        path("update") {
          put {
            parameters("timestamp", "field", "value") { (ts, field, valueStr) =>
              val bsonValue: Option[BsonValue] = field.toLowerCase match {
                case "soil_moisture" | "temperature" | "air_humidity" | "ph" | "rainfall" =>
                  try Some(BsonDouble(valueStr.toDouble)) catch { case _: NumberFormatException => None }
                case "n" | "p" | "k" =>
                  try Some(BsonInt32(valueStr.toInt)) catch { case _: NumberFormatException => None }
                case _ => None
              }

              bsonValue match {
                case Some(v) =>
                  val success = SmartFarmingApp.updateField(ts, field, v)
                  if (success) complete(StatusCodes.OK, s"Field '$field' updated.")
                  else complete(StatusCodes.NotFound, "Timestamp not found.")
                case None =>
                  complete(StatusCodes.BadRequest, "Invalid field or value type.")
              }
            }
          }
        },
        
        path("delete") {
          delete {
            parameter("timestamp".as[String]) { ts =>
              val success = SmartFarmingApp.deleteByTimestamp(ts)
              if (success) complete(StatusCodes.OK, "Deleted successfully")
              else complete(StatusCodes.NotFound, "Timestamp not found")
            }
          }
        },
        
       pathPrefix("api") {
  path("predict_water") {
    get {
      complete {
        try {
          val jsonStr = SmartFarmingApp.predict_water()
          parse(jsonStr) match {
            case Right(json) => json
            case Left(err) =>
              StatusCodes.InternalServerError -> Json.obj(
                "error" -> Json.fromString("Invalid JSON from Python"),
                "details" -> Json.fromString(err.getMessage)
              )
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
            StatusCodes.InternalServerError -> Json.obj(
              "error" -> Json.fromString(e.getMessage),
              "type"  -> Json.fromString("prediction_failed")
            )
        }
      }
    }
  }
}

      )
    }
}