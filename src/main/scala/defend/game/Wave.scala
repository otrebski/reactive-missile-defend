package defend.game

import defend.model.{ AlienWeapon, Position, WeaponInAction }

case class Wave(name: String, weaponsInAction: List[WeaponInAction[AlienWeapon]])

object Wave {

  private val letterA =
    """
      |  XXX
      | X   X
      | XXXXX
      | X   X
    """.stripMargin

  private val letterB =
    """
      |XXXX
      |X   X
      |XXXX
      |X   X
      |XXXX
    """.stripMargin

  private val letterC =
    """
      | XXXX
      |X
      |X
      |X
      | XXXX
    """.stripMargin

  private val letterD =
    """
      |XXXX
      |X   X
      |X   X
      |X   X
      |XXXX
    """.stripMargin

  private val letterE =
    """
      |XXXXX
      |X
      |XXXXX
      |X
      |XXXXX
    """.stripMargin

  private val letterF =
    """
      |XXXXX
      |X
      |XXXXX
      |X
      |X
    """.stripMargin

  private val letterG =
    """
      |XXXX
      |X
      |X  XX
      |X   X
      |XXXXX
    """.stripMargin

  private val letterH =
    """
      |X   X
      |X   X
      |XXXXX
      |X   X
      |X   X
    """.stripMargin

  private val letterI =
    """
      | XXX
      |  X
      |  X
      |  X
      | XXX
    """.stripMargin

  private val letterJ =
    """
      |   X
      |   X
      |   X
      |X  X
      | XXX
    """.stripMargin
  private val letterK =
    """
      |X   X
      |X  X
      |XXX
      |X  X
      |X   X
    """.stripMargin

  private val letterL =
    """
      |X
      |X
      |X
      |X
      |XXXX
    """.stripMargin

  private val letterM =
    """
      |X   X
      |XX XX
      |X X X
      |X   X
      |X   X
    """.stripMargin

  private val letterN =
    """
      |X   X
      |XX  X
      |X X X
      |X  XX
      |X   X
    """.stripMargin

  private val letterO =
    """
      | XXX
      |X   X
      |X   X
      |X   X
      | XXX
    """.stripMargin

  private val letterP =
    """
      |XXXX
      |X  X
      |XXXX
      |X
      |X
    """.stripMargin

  private val letterQ =
    """
      | XXX
      |X   X
      |X   X
      | XX X
      |    X
    """.stripMargin

  private val letterR =
    """
      |XXXX
      |X   X
      |XXXX
      |X  X
      |X   X
    """.stripMargin

  private val letterS =
    """
      | XXX
      |    X
      |  X
      |X
      | XXX
    """.stripMargin

  private val letterT =
    """
      |XXXXX
      |  X
      |  X
      |  X
      |  X
      |  """.stripMargin

  private val f = new (String => List[Position]) {
    override def apply(s: String): List[Position] = {
      val withIndex = s.lines.filter(!_.trim.isEmpty).zipWithIndex.toList
      val a = withIndex.flatMap { t =>
        t._1.zipWithIndex.filter(_._1 == 'X') map (y => Position(y._2 * 16, -t._2 * 16))
      }
      a.toList
    }
  }

  val lettersMap = Map(
    "a" -> letterA,
    "b" -> letterB,
    "c" -> letterC,
    "d" -> letterD,
    "e" -> letterE,
    "f" -> letterF,
    "g" -> letterG,
    "h" -> letterH,
    "i" -> letterI,
    "j" -> letterJ
  ).mapValues(f)

}
