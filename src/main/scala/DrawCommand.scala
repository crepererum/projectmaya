case class DrawCommand(root: DrawNode, z: Integer = 0)

abstract class DrawNode {}
case class Collection(chields: IndexedSeq[DrawNode]) extends DrawNode
case class Transform(chield: DrawNode, x: Double = 0, y: Double = 0, r: Double = 0, h: Double = 1, w: Double = 1) extends DrawNode
case class Tile(id: String = "dummy") extends DrawNode

case object CreateYourDrawCommand
case object RemoveDrawCommand
