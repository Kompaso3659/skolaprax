import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

object AkkaHttpServer extends App {
  implicit val actorSystem = ActorSystem()
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val ec = actorSystem.dispatcher

  val route = {
    concat(
      path("hello_to") {
        get {
          parameters("name".as[String]) { name =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Hello to $name"))
          }
        }
      },
      path("hello") {
        get {
          onComplete(Future {
            "ourString"
          }) {
            case Success(value) => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello to everyone"))
            case Failure(exception) => complete(InternalServerError, s"An error occurred: ${exception.getMessage}")
          }
        }
      },
      pathEndOrSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Response from server"))
        }
      },
      pathPrefix("user" / LongNumber)( userId => concat(
        pathEndOrSingleSlash {
          get {
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"user detail of user $userId"))
          }
        },
        path("delete") {
          decodeRequest {
            post {
              entity(as[String]) { ent: String =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"deleting user $userId with params $ent"))
              }
            }
          }
        }
      )),
      pathPrefix("nasa") {
        pathEndOrSingleSlash {
          get {
            parameters("date".as[String]) { date =>
              onComplete(NasaApiClient.getImageOfTheDay(date)) {
                case Success(Some(image)) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>${image.title}</h1><img src='${image.url}'><p>${image.explanation}</p>"))
                case Success(None) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"No image available for date $date"))
                case Failure(exception) =>
                  complete(InternalServerError, s"An error occurred: ${exception.getMessage}")
              }
            }
          }
        }
      }
    )
  }

  val bindingFut = for {
    binding <- Http().newServerAt("localhost", 8080).bind(route)
    _ = println(s"Server running on ${binding.localAddress.getHostName}:${binding.localAddress.getPort}")
  } yield binding

  StdIn.readLine()
  bindingFut.flatMap(_.unbind()).andThen(_ => actorSystem.terminate())
}