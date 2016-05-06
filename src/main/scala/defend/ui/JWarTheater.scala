package defend.ui

import java.awt.event.{ ActionEvent, ActionListener, MouseEvent }
import java.awt.geom.AffineTransform
import java.awt.image.{ AffineTransformOp, BufferedImage }
import java.awt.{ Color, Font, FontMetrics, Polygon }
import javax.imageio.ImageIO
import javax.swing.Timer

import defend.PersistenceMonitor.{ PersistenceError, PersistenceOk, PersistenceState }
import defend._
import defend.model._
import defend.ui.CommandCenterIcons._
import defend.ui.JWarTheater.CommandCenterPopupAction
import defend.ui.StatusKeeper.Protocol.LostMessages

import scala.collection.immutable.Queue
import scala.language.implicitConversions

//import scala.language.implicitConversions

import scala.swing._
import scala.swing.event.{ MouseClicked, MouseMoved, MousePressed, MouseReleased }

class JWarTheater(
  var warTheater:               WarTheater,
  autoDownUnreachableAfter:     Long,
  var offlineSince:             Option[Long]                   = None,
  var showGrid:                 Boolean                        = false,
  var showTracks:               Boolean                        = false,
  var dragListener:             Option[(DragEvent) => Unit]    = None,
  var commandCenterPopupAction: List[CommandCenterPopupAction] = List.empty,
  val timeProvider:             () => Long                     = System.currentTimeMillis
)
    extends Component {

  var paintDebug = true
  //Nodes go down with  autoDownUnreachableAfter milliseconds after node is unreachable.
  //I assume that time before command center going down and detect that node is unreachable is between E and PI in sec :)
  // real value is calculated with The Phi Accrual Failure Detector.
  private val nodeDownCorrection = 1000 * (Math.PI + Math.E) / 2

  preferredSize = new Dimension(warTheater.landScape.width, warTheater.landScape.height)
  maximumSize = preferredSize
  minimumSize = preferredSize

  private val debugFont: swing.Font = new swing.Font("Courier", Font.PLAIN, 12)
  private val towerDetailsFont: swing.Font = new swing.Font("Courier", Font.BOLD, 14)
  private val towerInfoFont: swing.Font = new swing.Font("Courier", Font.BOLD, 13)
  private val offlineTowerFont: swing.Font = new swing.Font("Arial", Font.BOLD, 14)
  private val offlineStatusKeeperFont: swing.Font = new swing.Font("Arial", Font.BOLD, 32)
  private val cityIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/city.png"))
  private val defenceIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/tower_ready.png"))
  private val defenceIconOffline: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/tower_offline.png"))
  private val defenceIconReloading: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/tower_reloading.png"))
  private val defenceIconInfected = List(
    ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/poop-smiley.png")),
    ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/poop-smiley-sad.png"))
  )
  private val humanRocket: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/human_rocket.png"))
  private val alienRocket: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/alien_rocket.png"))
  private val nukeIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/radioactivity.png"))
  private val bombIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/bomb.png"))
  private val empIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/game.png"))
  private val serverIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/server-cloud.png"))
  private val okIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/tick-octagon-frame.png"))
  private val errorIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/cross-octagon-frame.png"))
  private val unknownIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/question-octagon-frame.png"))
  private val eye: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/eye.png"))

  private val lostMessageIcon: BufferedImage = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/mail--exclamation.png"))

  private var paintTimestamp = Queue[Long]()
  private var selectedTower: Option[DefenceTowerStatus] = None
  private var selectedCommandCenter: Option[String] = None

  private val angleErrorColor: swing.Color = new swing.Color(1f, 1f, 0f, 0.3f)
  private val selectedTowerInfoColor = new swing.Color(1f, 1f, 1f, 0.5f)
  listenTo(mouse.clicks)
  listenTo(mouse.moves)

  private var clickPoint: Option[Position] = None
  private var commandCenterOnScreen: Map[Rect, String] = Map.empty[Rect, String]

  reactions += {
    case a: MouseClicked if a.peer.getButton == MouseEvent.BUTTON3 =>
      commandCenterOnScreen
        .find(rn => rn._1.contains(a.point))
        .map(_._2)
        .foreach { name =>
          val popup = new PopupMenu
          commandCenterPopupAction.foreach { action =>
            val item = new MenuItem(new Action(s"${action.name} $name") {
              def apply() = {
                action.action.apply(name)
              }
            })
            popup.contents += item
          }
          popup.show(this, a.point.x, a.point.y)
        }
    case a: MouseMoved =>
      val point: Point = a.point
      selectedTower = warTheater.defence.find {
        d =>
          val p: Position = d.defenceTower.position
          closeEnough(p, Position(point.x, warTheater.landScape.height - point.y), 16)
      }
      selectedCommandCenter = commandCenterOnScreen
        .find(rn => rn._1.contains(a.point))
        .map(_._2)
    case a: MousePressed =>
      clickPoint = Some(Position(a.point.x, warTheater.landScape.height - a.point.y))
    case a: MouseReleased =>
      val releasePoint = Position(a.point.x, warTheater.landScape.height - a.point.y)
      val moveVector: MoveVector = calculateDirection(clickPoint.get, releasePoint)
      dragListener.foreach(f => f(DragEvent(clickPoint.get, moveVector)))
      clickPoint = None

  }

  private def paintIcon(g: Graphics2D, landScape: LandScape, img: Image, x: Double, y: Double) = {
    val normalY = landScape.height - y
    val centerX = x - img.getWidth(null) / 2
    val centerY = normalY - img.getHeight(null) / 2
    g.drawImage(img, centerX, centerY, null)
  }

  def paintGrid(g: Graphics2D, landScape: LandScape): Unit = {
    g.setColor(new Color(0, 0, 0, 30))
    val rangeX: Range = Range(0, landScape.width, 50)
    val rangeY: Range = Range(0, landScape.height, 50)
    for (x <- rangeX) {
      g.drawLine(x, 0, x, landScape.height)
    }
    for (y <- rangeY) {
      g.drawLine(0, y, landScape.width, y)
    }
  }

  override def paint(g: Graphics2D): Unit = {
    val paintingStart: Long = timeProvider()
    g.setColor(new swing.Color(51, 204, 255))
    g.fillRect(0, 0, size.getWidth, size.getHeight)
    val landScape: LandScape = warTheater.landScape
    paintSky(g, landScape)
    paintExplosions(g, landScape, warTheater.explosions)
    paintLandScape(g, landScape)
    paintCities(g, warTheater.city, landScape)
    paintDefence(g, warTheater.defence, landScape, warTheater.lostMessages)
    paintAlienWeapon(g, landScape, warTheater.alienWeapons)
    paintHumanWeapon(g, warTheater.humanWeapons, landScape)
    paintCommandCentres(g, warTheater.commandCentres, warTheater.defence, warTheater.clusterLeader, landScape)
    paintPersistenceStateAndStatusKeeper(g, warTheater.persistenceState, warTheater.statusKeeper, landScape)
    paintPoints(g, warTheater.points, landScape)
    paintGameOver(g, warTheater.city, landScape)
    if (showGrid) {
      paintGrid(g, landScape)
    }
    paintSelectedDefence(g, warTheater.landScape, warTheater.defence, warTheater.lostMessages, warTheater.recoveryTime)
    if (paintDebug) {
      paintDebug(g, paintingStart, timeProvider() - warTheater.timestamp)
    }
    paintOffline(g, landScape)
  }

  def paintOffline(g: Graphics2D, landScape: LandScape): Unit = {
    offlineSince.foreach {
      since =>
        val duration = timeProvider() - since
        val alpha = scala.math.min(1f * duration / 5000, 0.7f)
        val gray: swing.Color = new swing.Color(0f, 0f, 0f, alpha)

        g.setColor(gray)
        g.fillRect(0, 0, landScape.width, landScape.height)
        g.setColor(Color.RED)
        g.setFont(offlineStatusKeeperFont)
        val string: String = f"Offline ${duration.toFloat / 1000}%.1fs"
        val x = landScape.width / 2 - g.getFontMetrics.stringWidth(string) / 2
        g.drawString(string, x, landScape.height / 2)
    }
  }

  def paintGameOver(d: Graphics2D, statuses: List[City], scape: LandScape) = {
    if (statuses.nonEmpty && statuses.map(_.condition).sum == 0) {
      val s = "GAME OVER"
      d.setFont(new Font("Arial", Font.BOLD, 60))
      val hue: Float = (timeProvider() % 400).toFloat / 400
      val saturation: Float = (timeProvider() % 600).toFloat / 600
      val brightness: Float = (timeProvider() % 800).toFloat / 800
      val c: Int = Color.HSBtoRGB(hue, saturation, brightness)
      d.setColor(new Color(c))
      val metrics: FontMetrics = d.getFontMetrics
      val width: Int = metrics.stringWidth(s)
      val height: Int = metrics.getHeight
      d.drawString(s, scape.width / 2 - width / 2, scape.height / 2 - height / 2)
    }
  }

  def paintPoints(d: Graphics2D, points: Integer, scape: LandScape) = {
    val font: swing.Font = new swing.Font("Arial", Font.BOLD, 20)
    d.setFont(font)
    val string = s"Points: $points"
    val metrics: FontMetrics = d.getFontMetrics
    d.setColor(Color.BLACK)
    val width: Int = metrics.stringWidth(string) + 11
    val x: Int = scape.width / 2 - width / 2
    val y: Int = scape.height - metrics.getHeight
    d.fillRect(x - 1, y - 4, width, metrics.getHeight + 1)
    d.setColor(Color.WHITE)
    d.drawRect(x - 1, y - 4, width, metrics.getHeight + 3)
    d.drawString(string, x + 5, scape.height - 5)
  }

  def paintExplosions(d: Graphics2D, landScape: LandScape, explosions: List[ExplosionEvent]) = {

    explosions.foreach {
      e =>

        val colors = e.explosion.weapon match {
          case e: AlienEmp     => List(Color.WHITE, Color.CYAN, Color.YELLOW, Color.BLUE)
          case e: HumanMissile => List(Color.WHITE, Color.PINK, Color.YELLOW, Color.DARK_GRAY)
          case _               => List(Color.ORANGE, Color.CYAN, Color.YELLOW, Color.RED)
        }
        val c = timeProvider() / 100 % 6 match {
          case 0 => colors.head
          case 4 => colors(1)
          case 8 => colors(2)
          case _ => colors(3)
        }
        val transparency: Int = Math.min(Math.max((e.expire * 255).toInt, 0), 255)
        d.setColor(new Color(c.getRed, c.getGreen, c.getBlue, transparency))
        val radius: Int = e.explosion.weapon.explosionRadius

        val p = e.explosion.position.copy(y = landScape.height - e.explosion.position.y)
        d.fillOval(p.x - radius, p.y - radius, 2 * radius, 2 * radius)
        d.setColor(new Color(0, 0, 0, transparency))
        d.drawOval(p.x - radius, p.y - radius, 2 * radius, 2 * radius)
        val expire: Float = 1 - e.expire * e.expire * e.expire
        e.explosion.weapon match {
          case x: AlienNuke =>
            d.setColor(Color.WHITE)
            d.drawOval((p.x - radius * expire).toInt, (p.y - radius * expire).toInt, (2 * radius * expire).toInt, (2 * radius * expire).toInt)
          case e: Any =>
        }

    }
  }

  def paintDebug(g: Graphics2D, paintingStart: Long, delay: Long): Unit = {
    g.setColor(Color.BLACK)

    g.setFont(debugFont)
    val current: Long = timeProvider()
    paintTimestamp = paintTimestamp.+:(current)
    if (paintTimestamp.size > 10) {
      val dequeue: Long = paintTimestamp.last
      paintTimestamp = paintTimestamp.init
      val fps = 1000 * 10 / (current - dequeue)
      //      g.setColor(Color.BLACK)
      g.drawString(s"FPS: $fps", 10, 20)
    }
    g.drawString(s"Alien missiles: ${warTheater.alienWeapons.size}", 10, 30)
    g.drawString(s"Human missiles: ${warTheater.humanWeapons.size}", 10, 40)
    val paintDuration = current - paintingStart
    g.drawString(s"Painting took ${paintDuration}ms", 10, 50)
    g.drawString(s"Situation from ${delay}ms ago", 10, 60)
  }

  def paintDefence(d: Graphics2D, towers: List[DefenceTowerStatus], landScape: LandScape, lostMessages: List[LostMessages]): Unit = {
    towers.foreach {
      t =>
        val icon = t match {
          case t: DefenceTowerStatus if !t.isUp =>
            defenceIconOffline
          case t: DefenceTowerStatus => t.defenceTowerState match {
            case DefenceTowerReloading => defenceIconReloading
            case DefenceTowerReady     => defenceIcon
            case DefenceTowerInfected  => defenceIconInfected(((timeProvider() / 100) % defenceIconInfected.size).toInt)
          }
          case _ => defenceIcon
        }

        val x: Int = t.defenceTower.position.x
        val y: Double = t.defenceTower.position.y
        paintIcon(d, landScape, icon, x, y + icon.getHeight / 2)

        //paint experience
        d.setColor(Color.CYAN)
        d.fillRect(x - icon.getWidth / 2, landScape.height - y + 5, icon.getWidth, t.level)
        d.setColor(Color.BLACK)
        d.drawRect(x - icon.getWidth / 2, landScape.height - y + 5, icon.getWidth, 10)
        d.setFont(debugFont)

        //paint offline time
        d.setColor(Color.BLACK)
        if (!t.isUp) {
          t.lastMessageTimestamp.foreach {
            ts =>
              d.setFont(offlineTowerFont)
              val time = timeProvider() - ts
              val toDisplay: String = if (time > 100000) ">100s" else s"${time / 1000}s"
              val stringWidth: Int = d.getFontMetrics.stringWidth(toDisplay)
              d.drawString(toDisplay, x - stringWidth / 2, landScape.height - y - d.getFontMetrics.getHeight - 10)
          }
        }
        //paint command center
        t.commandCenterName.map(iconForCommandCentre).forall {
          img =>
            paintIcon(d, landScape, img, x, y - icon.getHeight - 10)
        }

        //paint lost messages
        val lostMessagesByTower = lostMessages
          .filter(_.tower == t.defenceTower)
          .map(_.lostMessages)
          .sum

        if (lostMessagesByTower > 0) {
          d.setFont(debugFont)
          d.setColor(Color.RED)
          val metrics: FontMetrics = d.getFontMetrics()
          val s: String = s"$lostMessagesByTower"
          metrics.stringWidth(s)
          d.drawString(s, x - metrics.stringWidth(s) / 2, landScape.height - y + 45)
          if (timeProvider() % 1000 < 800) {
            d.drawImage(lostMessageIcon, x - lostMessageIcon.getWidth / 2, landScape.height - y + 45, null)
          }
        }

        //paint if CC is selected
        if (selectedCommandCenter.isDefined && selectedCommandCenter == t.commandCenterName) {
          d.setColor(selectionColor(timeProvider()))
          val rectX: Int = x - 12
          val rectY: Int = landScape.height - y - 24
          val width: Int = 24
          val height: Int = 64
          val arc: Int = 10
          d.drawRoundRect(rectX, rectY, width, height, arc, arc)
          d.drawRoundRect(rectX + 1, rectY + 1, width - 2, height - 2, arc, arc)
        }
    }
  }

  def selectionColor(timestamp: Long): swing.Color = {
    val int = (timestamp % 1000).toDouble //(0-255)
    val d = (255 * Math.sin(int * Math.PI / 1000)).toInt
    new swing.Color(255, d, d)
  }

  def paintSelectedDefence(g: Graphics2D, landScape: LandScape, defence: List[DefenceTowerStatus], lostMessages: List[LostMessages], recoveryTime: Map[String, Long]) = {
    selectedTower.flatMap(t => defence.find(_.defenceTower.position == t.defenceTower.position)).foreach {
      t =>
        val lostMessagesCount = lostMessages.filter(_.tower == t.defenceTower).map(_.lostMessages).sum

        val position: Position = t.defenceTower.position
        val range: Int = rangeForLevel(t.level)
        val x = position.x - range
        val y = landScape.height - (position.y + range)
        g.setColor(Color.WHITE)
        g.drawArc(x.toInt, y.toInt, 2 * range, 2 * range, 0, 180)

        val degrees: Double = angleErrorForLevel(t.level).toDegrees
        g.setColor(angleErrorColor)
        g.fillArc(x.toInt, y.toInt, 2 * range, 2 * range, 90 - degrees / 2, degrees)

        val info =
          f"""|${t.defenceTower.name}
              |Level: ${t.level}
              |Range: $range
              |Angle error: $degrees%2.1f°
              |Recovery time: ${recoveryTime.get(t.defenceTower.name).map(s => s"${s}ms").getOrElse("Unknown")}
              |Lost messages: $lostMessagesCount""".stripMargin
        g.setFont(towerDetailsFont)
        val metrics: FontMetrics = g.getFontMetrics
        val height = metrics.getHeight
        import scala.math._
        val lines: Array[String] = info.split("\n")
        val maxWidth = lines.map(metrics.stringWidth).max
        val xPox: Double = min(landScape.width - maxWidth, max(0, position.x - maxWidth / 2))

        val border = 4
        g.setColor(selectedTowerInfoColor)
        val yPos: Int = landScape.height - position.y - (lines.length + 1) * height
        g.fillRect(xPox - border, yPos - height - border, maxWidth + 2 * border, lines.length * height + 2 * border)
        g.fillPolygon(new Polygon(
          Array(position.x.toInt, position.x.toInt - height, position.x.toInt + height),
          Array(yPos + (lines.length + 1) * height, yPos + (lines.length - 1) * height + border, yPos + (lines.length - 1) * height + border),
          3
        ))
        g.setColor(Color.BLACK)
        lines.zipWithIndex.foreach(l => {
          g.drawString(l._1, xPox, yPos + l._2 * height)
        })

    }
  }

  def paintTrack(d: Graphics2D, landScape: LandScape, w: WeaponInAction[Weapon]): Unit = {
    //Rocket at 357, 550 -> 76, 150.0, move direction = 4.243512022491318
    val color = w.weapon match {
      case a: AlienWeapon => new Color(255, 0, 0, 30)
      case _              => new Color(120, 120, 120, 30)
    }
    val endY: Double = w.weapon match {
      case a: AlienWeapon => w.explodeVelocity.getOrElse(landScape.groundLevel)
      case _              => w.explodeVelocity.getOrElse(landScape.height * 2)
    }
    val startX = w.position.x
    val startY = w.position.y
    //x => (moveVector.speed * Math.cos(moveVector.direction)
    val r = (endY - startY) / Math.sin(w.moveVector.direction)
    val endX = r * Math.cos(w.moveVector.direction)
    //    val endY = r * Math.sin(w.moveVector.direction)
    d.setColor(color)
    d.drawLine(startX, landScape.height - startY, startX + endX, landScape.height - endY)
    w.explodeVelocity.foreach(y => {
      val yTranslated: Int = landScape.height - y - 3
      d.drawLine(startX + endX - 3, yTranslated, startX + endX + 3, yTranslated)
    })

  }

  def paintAlienWeapon(d: Graphics2D, landScape: LandScape, actions: List[WeaponInAction[AlienWeapon]]): Unit = {
    actions.foreach {
      w =>
        val icon = w.weapon match {
          case a: AlienBomb    => bombIcon
          case a: AlienEmp     => empIcon
          case a: AlienMissile => alienRocket
          case a: AlienNuke    => nukeIcon
        }
        if (showTracks) {
          paintTrack(d, landScape, w)
        }
        val rotatedIcon: BufferedImage = rotateIcon(w.moveVector, icon)
        paintIcon(d, landScape, rotatedIcon, w.position.x, w.position.y)
    }
  }

  def rotateIcon(vector: MoveVector, icon: BufferedImage): BufferedImage = {
    val transform = new AffineTransform()
    transform.rotate(-vector.direction, icon.getWidth / 2, icon.getHeight / 2)
    val op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
    val rotated = op.filter(icon, null)
    rotated
  }

  def paintHumanWeapon(d: Graphics2D, actions: List[WeaponInAction[HumanWeapon]], landScape: LandScape): Unit = {
    actions.foreach {
      w =>
        w.weapon match {
          case a: HumanMissile =>
            val x = w.position.x
            val y = w.position.y
            val icon: BufferedImage = rotateIcon(w.moveVector, humanRocket)
            if (showTracks) {
              paintTrack(d, landScape, w)
            }
            paintIcon(d, landScape, icon, x, y)
        }

    }
  }

  def paintCommandCentres(d: Graphics2D, centers: List[CommandCenter], towers: List[DefenceTowerStatus], clusterLeader: Option[String], scape: LandScape): Unit = {
    commandCenterOnScreen = Map.empty[Rect, String]
    val font: swing.Font = new swing.Font("Courier", Font.PLAIN, 12)
    d.setFont(font)
    val metrics: FontMetrics = d.getFontMetrics(font)
    val fontHeight: Int = metrics.getHeight
    val xPos: Int = 10
    val yPos: Int = scape.groundLevel - 90
    val height = 24
    val rightCorner = centers.foldLeft(xPos) { (xPos, center) =>
      val towersInCc = towers.count(_.commandCenterName.contains(center.name))
      val name: String = s"${clusterAddressToHostPort(center.name)} => $towersInCc"
      val icon: BufferedImage = iconForCommandCentre(center)
      val width = metrics.stringWidth(name) + serverIcon.getWidth * 5
      val r: Rect = Rect(xPos, scape.height - yPos, width, height)
      commandCenterOnScreen = commandCenterOnScreen.updated(r, center.name)

      d.setColor(Color.BLACK)

      d.fillRect(r.x, r.y, r.width, r.height)
      val ts = timeProvider() - center.lastMessageTimestamp
      val widthDelay: Int = Math.min(Math.max((r.width * ts / (autoDownUnreachableAfter + nodeDownCorrection)).toInt, 0), r.width)
      d.setColor(Color.DARK_GRAY)
      d.fillRect(r.x, r.y, widthDelay, r.height)
      d.setColor(Color.LIGHT_GRAY)
      d.drawRect(r.x, r.y, widthDelay, r.height)
      d.setColor(Color.WHITE)
      d.drawRect(r.x, r.y, r.width, r.height)
      val towerSelectedWithThisCc = selectedTower.exists {
        _.commandCenterName.contains(center.name)
      }

      if (selectedCommandCenter.contains(center.name) || towerSelectedWithThisCc) {
        d.setColor(selectionColor(timeProvider()))
        for (i <- 1 until 4) {
          d.drawRoundRect(r.x + i, r.y + i, r.width - 2 * i, r.height - 2 * i, r.height, r.height)
        }
      }

      d.setColor(center.status match {
        case CommandCenterUnreachable => Color.ORANGE
        case CommandCenterOnline      => Color.GREEN
        case CommandCenterOffline     => Color.RED
      })
      d.drawString(name, xPos + serverIcon.getWidth * 2.3, scape.height - yPos + fontHeight)
      paintIcon(d, scape, icon, xPos + icon.getWidth, yPos - fontHeight)
      clusterLeader.filter(a => a == center.name).foreach { _ =>
        paintIcon(d, scape, leaderIcon, xPos + icon.getWidth + leaderIcon.getWidth, yPos - fontHeight)
      }
      xPos + width
    }

    val upCount: Int = centers.count(c => c.status == CommandCenterOnline)
    val totalCount: Int = centers.size
    d.setColor(if (upCount.toFloat / totalCount < 0.4) Color.ORANGE else Color.GREEN)
    val s = s"Command centres [$upCount of $totalCount]"
    val rect: Rect = Rect(xPos, Math.max(scape.height - yPos - height, 100), Math.max(rightCorner - xPos, metrics.stringWidth(s) + 4), height)
    d.setColor(Color.BLACK)
    d.fillRect(rect.x, rect.y, rect.width, rect.height)
    d.setColor(Color.WHITE)
    d.drawRect(rect.x, rect.y, rect.width, rect.height)
    d.setColor(Color.WHITE)

    d.drawString(s, rect.x + (rect.width - metrics.stringWidth(s)) / 2, rect.y + (rect.height + metrics.getHeight) / 2)
  }

  def paintPersistenceStateAndStatusKeeper(g: Graphics2D, persistenceState: PersistenceState, statusKeeperNode: Option[String], scape: LandScape) = {
    val font: swing.Font = new swing.Font("Courier", Font.PLAIN, 12)
    val xPos: Int = 10
    val yPos: Int = scape.groundLevel - 120
    val height = 24
    val (color, icon) = persistenceState match {
      case p: PersistenceOk    => (Color.GREEN, okIcon)
      case p: PersistenceError => (Color.RED, errorIcon)
      case _                   => (Color.WHITE, unknownIcon)
    }
    val s = "  Persistence: "
    val metrics: FontMetrics = g.getFontMetrics(font)
    val fontHeight: Int = metrics.getHeight
    val rect: Rect = Rect(xPos, scape.height - yPos, metrics.stringWidth(s) + icon.getWidth + 10, height)
    g.setColor(Color.BLACK)
    g.fillRect(rect.x, rect.y, rect.width, rect.height)
    g.setColor(Color.WHITE)
    g.drawRect(rect.x, rect.y, rect.width, rect.height)
    g.setColor(color)
    g.drawString(s, rect.x, rect.y + (rect.height + fontHeight) / 2)
    g.drawImage(icon, rect.x + metrics.stringWidth(s), rect.y + (rect.height - icon.getHeight) / 2, null)

    val sk = clusterAddressToHostPort(statusKeeperNode.getOrElse("?"))
    val rect2 = Rect(rect.x + rect.width, scape.height - yPos, metrics.stringWidth(sk) + 2 * eye.getWidth + 15, height)
    g.setColor(Color.BLACK)
    g.fillRect(rect2.x, rect2.y, rect2.width, rect2.height)
    g.setColor(Color.WHITE)
    g.drawRect(rect2.x, rect2.y, rect2.width, rect2.height)
    g.setColor(Color.GREEN)
    g.drawImage(eye, rect2.x + 5, rect.y + (rect2.height - eye.getHeight) / 2, null)
    g.drawImage(eye, rect2.x + 5 + eye.getWidth, rect.y + (rect2.height - eye.getHeight) / 2, null)
    g.drawString(sk, rect2.x + 10 + 2 * eye.getWidth, rect.y + (rect2.height + fontHeight) / 2)
  }

  def paintCities(d: Graphics2D, cities: List[City], landScape: LandScape): Unit = {
    val font: swing.Font = new swing.Font("Courier", Font.BOLD, 10)
    d.setFont(font)
    val metrics: FontMetrics = d.getFontMetrics(font)
    cities.foreach {
      city =>
        if (city.condition > 0) {
          paintIcon(d, landScape, cityIcon, city.position.x, landScape.groundLevel + cityIcon.getHeight / 2)
        }
        val color = city match {
          case City(_, _, condition) if condition > 30 => Color.GREEN
          case City(_, _, condition) if condition > 0  => Color.ORANGE
          case City(_, _, _)                           => Color.RED
        }
        d.setColor(color)
        val string: String = s"${city.condition}%"
        d.drawString(string, city.position.x - metrics.stringWidth(string) / 2, landScape.height - landScape.groundLevel + 10)

    }
  }

  private def paintSky(g: Graphics2D, landScape: LandScape): Unit = {
    g.setColor(Color.WHITE)
    val xShift = ((timeProvider() / 500) % (landScape.width * 2) - 260).toInt
    g.fillOval(100 + xShift, 100, 260, 80)
    g.fillOval(130 + xShift, 70, 120, 140)
    g.fillOval(160 + xShift, 70, 120, 130)
    g.fillOval(230 + xShift, 70, 80, 110)
    g.fillOval(280 + xShift, 70, 80, 90)

  }

  private def paintLandScape(g: Graphics2D, landScape: LandScape): Unit = {
    g.setColor(new Color(0, 80, 0))
    g.fillRect(0, landScape.height - landScape.groundLevel, landScape.width, landScape.groundLevel)

  }

  def updateState(warTheater: WarTheater): Unit = {
    this.warTheater = warTheater
    revalidate()
    repaint()
  }

  implicit def doubleToInr(d: Double): Int = d.toInt

  def clusterAddressToHostPort(address: String): String = {
    //akka.tcp://defend@127.0.0.1:3002
    if (address.matches(".*@.*:.*")) {
      val split = address.split("[@:]")
      split(2) match {
        case "127.0.0.1" => s"port ${split(3)}"
        case _           => split(2)
      }
    } else {
      address
    }
  }

}

case class Rect(x: Int, y: Int, width: Int, height: Int) {
  def contains(point: Point): Boolean = {
    (point.x > x) && (point.x - x < width) && (point.y > y) && (point.y - y < height)
  }
}

object JWarTheater extends SimpleSwingApplication {
  override def top: Frame = new MainFrame {

    val points = Range(0, 800, 50).map(i => Position(i, 600 + 100 * Math.sin(i.toFloat / 40)))
    val landScapeA = LandScape(800, 600, 150)
    val (cities, defence) = Range(30, landScapeA.width, 28).foldRight((List.empty[City], List.empty[DefenceTower])) {
      (i, b) =>
        if (i % 5 == 1) {
          b.copy(_1 = City("A", Position(i, landScapeA.groundLevel), i % 100 + 1) :: b._1)
        } else {
          b.copy(_2 = DefenceTower("A", Position(i, landScapeA.groundLevel)) :: b._2)
        }
    }
    //    val cities =
    //List[City](City("Chicago", 10, 100), City("New York", 100, 100))
    //    val defence = List[DefenceTower](DefenceTower("A", 200), DefenceTower("B", 400))
    val enemyWeapons = List(
      WeaponInAction(AlienBomb(10, 10), Position(400, 500), MoveVector(-Math.PI / 2, 1)),
      WeaponInAction(AlienBomb(100, 10), Position(450, 530), MoveVector(-Math.PI / 3, 1)),
      WeaponInAction(AlienNuke(100, 10), Position(350, 530), MoveVector(-Math.PI / 2, 1)),
      WeaponInAction(AlienMissile(100, 10), Position(550, 560), MoveVector(-Math.PI, 1)),
      WeaponInAction(AlienMissile(100, 10), Position(570, 560), MoveVector(-Math.PI * 0.55, 1)),
      WeaponInAction(AlienMissile(100, 10), Position(590, 560), MoveVector(-Math.PI * 0.75, 1)),
      WeaponInAction(AlienMissile(100, 10), Position(610, 560), MoveVector(-Math.PI * 1.25, 1)),
      WeaponInAction(AlienEmp(100, 10), Position(150, 550), MoveVector(-Math.PI * 0.25, 2))
    )

    val humanWeapons = List(
      WeaponInAction(HumanMissile("1", 10, 20), Position(200, 200), MoveVector(Math.PI * 0.5, 6)),
      WeaponInAction(HumanMissile("2", 10, 20), Position(220, 200), MoveVector(Math.PI * 0.5, 8)),
      WeaponInAction(HumanMissile("3", 10, 20), Position(240, 200), MoveVector(Math.PI * 0.5, 10)),
      WeaponInAction(HumanMissile("4", 10, 20), Position(260, 200), MoveVector(Math.PI * 0.5, 12)),
      WeaponInAction(HumanMissile("5", 10, 20), Position(280, 200), MoveVector(Math.PI * 0.5, 14)),
      WeaponInAction(HumanMissile("6", 10, 20), Position(300, 200), MoveVector(Math.PI * 0.5, 16)),
      WeaponInAction(HumanMissile("7", 10, 20), Position(320, 4200), MoveVector(Math.PI * 0.5, 18))
    )

    val commandCentres = List(
      CommandCenter("akka.tcp://a@192.168.2.11:3000", status = CommandCenterOnline, 10000),
      CommandCenter("akka.tcp://a@192.168.2.12:3000", status = CommandCenterUnreachable, 7000),
      CommandCenter("akka.tcp://a@192.168.2.13:3000", status = CommandCenterOffline, 0)
    )

    val explosions = List(
      ExplosionEvent(Explosion(Position(100, 100), AlienMissile(10, 10)), 0.2f),
      ExplosionEvent(Explosion(Position(400, 100), AlienMissile(10, 20)), 0.4f),
      ExplosionEvent(Explosion(Position(500, 100), AlienMissile(10, 30)), 0.9f)
    )

    def status(i: Int): DefenceTowerState = i % 7 match {
      case 3 => DefenceTowerReloading
      case 4 => DefenceTowerReloading
      case 5 => DefenceTowerReloading
      case 6 => DefenceTowerInfected
      case _ => DefenceTowerReady
    }

    private val towerStatuses: List[DefenceTowerStatus] = defence.zipWithIndex.map(t =>
      DefenceTowerStatus(
        defenceTower         = t._1,
        isUp                 = t._2 % 3 != 2,
        defenceTowerState    = status(t._2),
        commandCenterName    = Some(commandCentres(t._2 % commandCentres.size).name),
        lastMessageTimestamp = Some(400)
      ))

    val lostMessages = towerStatuses
      .map { t =>
        LostMessages(t.defenceTower, t.defenceTower.position.x.toInt % 56, 0)
      }
      .filter(_.lostMessages > 0)

    towerStatuses.foreach {
      t =>
        val lostMessagesByTower = lostMessages
          .filter(_.tower == t.defenceTower)
          .map(_.lostMessages)
          .sum
    }

    var warTheater: WarTheater = WarTheater(towerStatuses, cities, enemyWeapons,
      humanWeapons, landScape = landScapeA, commandCentres = commandCentres, explosions = explosions, lostMessages = lostMessages)

    title = "Missile defend test"
    val dragFun = new ((DragEvent) => Unit) {
      override def apply(v1: DragEvent): Unit = {
      }
    }
    val timeProvider = new (() => Long) {
      //      override def apply(): Long = 10004
      override def apply(): Long = System.currentTimeMillis()
    }
    private val theater: JWarTheater = new JWarTheater(warTheater, 8 * 1000, Some(0), true,
      dragListener             = Some(dragFun),
      timeProvider             = timeProvider,
      commandCenterPopupAction = List(new CommandCenterPopupAction("Some action on", new (String => Unit) {
        override def apply(v1: String): Unit = println(s"Disconnecting $v1")
      })))
    contents = new BoxPanel(Orientation.Vertical) {
      contents += theater
      border = Swing.EmptyBorder(30, 30, 10, 30)
    }
    theater.showGrid = false
    theater.paintDebug = false
    theater.offlineSince = None
    pack()

    val timer = new Timer(10, new ActionListener {
      var last = System.currentTimeMillis()

      override def actionPerformed(e: ActionEvent): Unit = {
        val millis: Long = System.currentTimeMillis() - last
        val movedAlienWeapons: List[WeaponInAction[AlienWeapon]] = warTheater.alienWeapons.map { w =>
          val newPosition: Position = defend.move(w.position, w.moveVector, millis)
          w.copy(position = normalizePosition(newPosition, landScapeA))
        }

        var movedHumanWeapons = warTheater.humanWeapons.map { w =>
          val newPosition: Position = defend.move(w.position, w.moveVector, millis)
          w.copy(position = normalizePosition(newPosition, landScapeA))
        }

        val vector: MoveVector = movedHumanWeapons.head.moveVector

        val copy: MoveVector = vector.copy(direction = vector.direction + Math.PI / 190, speed = 21)
        movedHumanWeapons = movedHumanWeapons.head.copy(moveVector = copy) :: movedHumanWeapons.tail

        last = System.currentTimeMillis()
        val defenceTowerStatuses: List[DefenceTowerStatus] = warTheater.defence.map(x => x.copy(lastMessageTimestamp = Some(timeProvider() - 4000)))
        val cc: List[CommandCenter] = warTheater.commandCentres.map { cc =>
          val delay: Double = Math.pow(cc.name.substring(24, 25).toInt, 2) * 500
          cc.copy(lastMessageTimestamp = timeProvider() - delay.toLong)
        }
        warTheater = warTheater.copy(
          alienWeapons   = movedAlienWeapons,
          humanWeapons   = movedHumanWeapons,
          timestamp      = System.currentTimeMillis() + 7000,
          defence        = defenceTowerStatuses,
          commandCentres = cc
        )

        theater.updateState(warTheater)
      }

      def normalizePosition(p: Position, landScape: LandScape): Position = {
        val newX: Double = if (p.x < 0) landScape.width else if (p.x > landScape.width) 0 else p.x
        val newY: Double = if (p.y < landScape.groundLevel) landScape.height else if (p.y > landScape.height) landScape.groundLevel else p.y
        val normalized: Position = Position(newX, newY)
        normalized
      }

    })
    timer.setRepeats(true)
    timer.start()
    timer.setInitialDelay(1000)

    override def closeOperation(): Unit = {
      timer.stop()
      System.exit(0)
    }
  }

  case class CommandCenterPopupAction(name: String, action: (String => Unit))

}

