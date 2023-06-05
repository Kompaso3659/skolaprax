import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}
import spray.json._
import DefaultJsonProtocol._
import scala.concurrent.ExecutionContext

case class NasaApiResponse(date: String, explanation: String, media_type: String, title: String, url: String)

object JsonProtocol {
    implicit val responseFormat = jsonFormat5(NasaApiResponse)
}

class NasaApiClient(apiKey: String = "")(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) {
    import JsonProtocol._
  private val apiBaseUrl = "https://api.nasa.gov/planetary/apod"
  def getImageOfTheDay(date: String, hd: Boolean = false): Future[NasaApiResponse] = {
    val requestUrl = s"$apiBaseUrl?date=$date&api_key=$apiKey${if (hd) "&hd=true" else ""}"
    val request = HttpRequest(HttpMethods.GET, requestUrl)
    val responseFuture = Http().singleRequest(request)

    responseFuture.flatMap(response => response.status match {
      case StatusCodes.OK =>
        response.entity.toStrict(5.seconds).map { entity =>
          val json = entity.data.utf8String.parseJson
          json.convertTo[NasaApiResponse]
        }

      case _ =>
        response.discardEntityBytes()

        Future.failed(new RuntimeException(s"Unexpected status code ${response.status}"))
    })
  }
}

object AkkaHttpServer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  import JsonProtocol._
  val apiKey = "LSuFph5M85xV8HzueGPdzjU1RKWGWzx0ItC3LyJP"
  val nasaApiClient = new NasaApiClient(apiKey)
  val route = {
    concat(
      path("nasa") {
        get {
          parameters("date".as[String]) { date =>
            onComplete(nasaApiClient.getImageOfTheDay(date)) {
              case Success(response) =>
                complete(response.toJson.prettyPrint)
              case Failure(ex) =>
                complete(HttpResponse(StatusCodes.InternalServerError, entity = s"Failed to retrieve NASA data: ${ex.getMessage}"))
            }
          }
        }
      },

      pathEndOrSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Response from server"))
        }
      }
    )
  }

  val bindingFut = Http().newServerAt("localhost", 8080).bind(route)
  bindingFut.onComplete {
    case Success(binding) =>
      println(s"Server is listening on ${binding.localAddress}")
    case Failure(ex) =>
      println(s"Server could not start!")
      ex.printStackTrace()
      system.terminate()
  }
  StdIn.readLine()
  bindingFut
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
