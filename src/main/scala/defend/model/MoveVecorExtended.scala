package defend.model

import scala.language.implicitConversions

trait MoveVectorInExtended {
  implicit def hasInAngle(a: MoveVector): MvInAngle = {
    new MvInAngle(a)
  }

  class MvInAngle(mv: MoveVector) {

    import scala.math._

    def inAngleDegrees(start: Int, end: Int): MoveVector = {
      val direction: Double = mv.direction
      val newDirection: Double = max(min(direction, end.toDouble.toRadians), start.toDouble.toRadians)
      mv.copy(direction = newDirection)
    }
  }

}

object InAngle extends MoveVectorInExtended
