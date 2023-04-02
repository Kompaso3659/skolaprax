import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.Future

object BookPriceApp {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val apiKey = "your-api-key"
    val bookTitle = "The Hitchhiker's Guide to the Galaxy"
    val apiUrl = s"https://www.googleapis.com/books/v1/volumes?q=$bookTitle&key=$apiKey"

    val headers = List[HttpHeader]()
    val request = HttpRequest(uri = apiUrl, headers = headers)
    val responseFuture: Future[BookPriceData] =
      Source.single(request)
        .via(Http().outgoingConnectionHttps("www.googleapis.com"))
        .mapAsync(1)(response => Unmarshal(response.entity).to[BookPriceData])
        .runWith(Sink.head)

    responseFuture.onComplete {
      case scala.util.Success(bookPriceData) =>
        val bookPrice = bookPriceData.items.headOption.flatMap(_.saleInfo.listPrice.map(_.amount))
        bookPrice match {
          case Some(price) => println(s"The price of $bookTitle is $price")
          case None => println(s"Failed to retrieve book price for $bookTitle")
        }
      case scala.util.Failure(ex) =>
        println(s"Failed to retrieve book price data: ${ex.getMessage}")
    }
  }
}

case class BookPriceData(items: List[BookPriceItem])
case class BookPriceItem(saleInfo: SaleInfo)
case class SaleInfo(listPrice: Option[ListPrice])
case class ListPrice(amount: Double)