case class Color (r: Int, g: Int, b: Int)
case class Pos (x: Int, y: Int)
abstract class Pixel (val pos: Pos, val color: Color)
case class TransparentPixel (override val pos: Pos, override val color: Color,
transparency: Int)
    extends Pixel (pos, color)
case class RGBPixel (override val pos: Pos, override val color: Color)
    extends Pixel (pos, color)

val pixels = "0:255,0,0,0|1:0,255,0,0|2:0,0,255,0|3:0,0,0,255".stripMargin
  .split ("\\|")
  .zipWithIndex
  .collect { case (line, y) =>
    val Array (index, data) = line.split (":")
    val Array (r, g, b, t) = data.split (",")
    val color = Color (r.toInt, g.toInt, b.toInt)
    val pos = Pos (index.toInt, y)
    if (t == "null" || t == null) RGBPixel (pos, color)
    else TransparentPixel (pos, color, t.toInt)
  }

val redPixelByRow =
  pixels.groupBy (_.pos.y).mapValues (pixels => pixels.maxBy (_.color.r))
val mostTransparentPixel = pixels
  .filter (_.isInstanceOf [TransparentPixel])
  .maxBy (_.asInstanceOf [TransparentPixel].transparency)

println (mostTransparentPixel)
