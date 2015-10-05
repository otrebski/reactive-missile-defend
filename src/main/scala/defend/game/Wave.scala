package defend.game

import defend.model.{ LandScape, AlienWeapon, Position, WeaponInAction }

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
      |X   X
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

  private val letterU =
    """
      |X   X
      |X   X
      |X   X
      |X   X
      | XXX
      |  """.stripMargin

  private val letterV =
    """
      |X   X
      |X   X
      | X X
      | X X
      |  X
      |  """.stripMargin

  private val letterW =
    """
      |X   X
      |X   X
      |X   X
      |X X X
      | X X
      |  """.stripMargin

  private val letterY =
    """
      |X   X
      |X   X
      | XXX
      |  X
      |  X
      |  """.stripMargin

  private val letterZ =
    """
      |XXXXX
      |   X
      |  X
      | X
      |XXXXX
      |""".stripMargin

  private val letterSpace =
    """
      |
      |
      |
      |
      |
      |""".stripMargin

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
    "j" -> letterJ,
    "k" -> letterK,
    "l" -> letterL,
    "m" -> letterM,
    "n" -> letterN,
    "o" -> letterO,
    "p" -> letterP,
    "q" -> letterQ,
    "r" -> letterR,
    "s" -> letterS,
    "t" -> letterT,
    "u" -> letterU,
    "v" -> letterV,
    "w" -> letterW,
    "y" -> letterY,
    "z" -> letterZ,
    " " -> letterSpace

  ).mapValues(f)

  def stringPos(string: String, landScape: LandScape): List[Position] = {
    val default = lettersMap.get(" ").get
    val map: List[List[Position]] = string.toCharArray.toList.map(c => lettersMap.getOrElse(s"$c", default))
    val flatten: List[Position] = map
      .zipWithIndex
      .flatMap(indexPos => indexPos._1.map(p => p.copy(x = p.x + 16 * 7 * indexPos._2)))
      .map(p => p.copy(y = p.y + landScape.height + 64))

    flatten.toList
  }
}
