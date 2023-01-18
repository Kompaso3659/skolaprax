case class Color(r: Int, g: Int, b: Int)
case class Pos(x: Int, y: Int)
abstract class Pixel(val pos: Pos, val color: Color)
case class TransparentPixel(override val pos: Pos, override val color: Color, transparency: Int) extends Pixel(pos, color)
case class RGBPixel(override val pos: Pos, override val color: Color) extends Pixel(pos, color)

val screen = "0:255,0,0,0|1:0,255,0,0|2:0,0,255,0|3:0,0,0,255"

val pixels = screen
  .stripMargin
  .split("\\|")
  .zipWithIndex
  .flatMap {
    case (line, y) =>
      val Array(index, data) = line.split(":")
      if (index.isEmpty) None
      else {
        val Array(r, g, b, t) = data.split(",")
        val color = Color(r.toInt, g.toInt, b.toInt)
        val transparency = if (t == "null" || t == null) None else Some(t.toInt)
        transparency match {
          case Some(t) => Some(TransparentPixel(Pos(index.toInt, y), color, t))
          case None => Some(RGBPixel(Pos(index.toInt, y), color))
        }
      }
  }

val redPixelByRow = pixels.groupBy(_.pos.y).mapValues(pixels => pixels.maxBy(_.color.r))
val mostTransparentPixel = pixels.filter(_.isInstanceOf[TransparentPixel]).maxBy(_.asInstanceOf[TransparentPixel].transparency)

println(mostTransparentPixel)