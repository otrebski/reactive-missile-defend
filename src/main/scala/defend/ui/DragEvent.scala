package defend.ui

import defend.model.{ MoveVector, Position }

case class DragEvent(start: Position, moveVector: MoveVector)
