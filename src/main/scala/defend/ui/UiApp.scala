package defend.ui

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor._
import defend.cluster._
import defend.game._
import defend.model._
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration
import scala.swing._
import scala.swing.event.ButtonClicked

object UiApp extends SimpleSwingApplication
    with SharedLevelDb
    with DefendActorSystem
    with StatusKeeperSingleton
    with StatusKeeperProxy
    with TowerShard {

  import pl.project13.scala.rainbow.Rainbow._

  println("Starting".green)

  private val duration: Long = config.as[FiniteDuration]("akka.cluster.auto-down-unreachable-after").toMillis
  private val emptyWarTheater: WarTheater = WarTheater(List.empty, List.empty, List.empty, List.empty, LandScape(500, 500, 120), List.empty)
  private var gameEngine: Option[ActorRef] = None
  private val dragFunction = new ((DragEvent) => Unit) {
    override def apply(v1: DragEvent): Unit = {
      gameEngine.filter(_ => v1.moveVector.speed > 10).foreach(_ ! GameEngine.Protocol.AlienRocketFired(AlienMissile(1, 10), v1.start, v1.moveVector, None))
    }
  }
  private val jWarTheater: JWarTheater = new JWarTheater(emptyWarTheater, duration,
    showGrid     = false,
    showTracks   = true,
    dragListener = Some(dragFunction))
  private val uiUpdater = system.actorOf(UiUpdater.props(jWarTheater, statusKeeperProxy), "uiUpdater")

  override def top: Frame = new MainFrame {
    title = "Reactive Missile Defend  UI"

    private val buttonStart = new Button {
      text = "Start game"
    }
    private val LayoutTest: String = "test"
    private val LayoutTest_2: String = "test-2"
    private val defenceLayout = new ComboBox[String](List("Standard", "Low defence", "High defence", LayoutTest, LayoutTest_2))
    private val attackMode = new ComboBox[String](List("Standard-random", "Top drop", "Intelligent wave", "Test"))
    private val showGrid = new CheckBox("Show grid")
    showGrid.selected = jWarTheater.showGrid
    private val showTracks = new CheckBox("Show tracks")
    showTracks.selected = jWarTheater.showTracks

    listenTo(buttonStart)
    listenTo(showGrid)
    listenTo(showTracks)

    reactions += {
      case bc: ButtonClicked if bc.source == buttonStart =>
        val size1: Dimension = jWarTheater.size
        val landScape = LandScape(size.width, size1.height, 150)
        gameEngine.foreach(_ ! PoisonPill)
        val towerNamePrefix = new SimpleDateFormat("dd_HHmmss").format(new Date())
        //        val (cities, defence) = generateCityAndDefence(50, landScape.groundLevel, 30, towersPerCity)
        val (cities, defence) = defenceLayout.selection.item match {
          case "Standard"     => generateCityAndDefence(landScape.width, landScape.groundLevel, 30, 4, towerNamePrefix)
          case "Low defence"  => generateCityAndDefence(landScape.width, landScape.groundLevel, 50, 2, towerNamePrefix)
          case "High defence" => generateCityAndDefence(landScape.width, landScape.groundLevel, 20, 6, towerNamePrefix)
          case LayoutTest => (
            List(City("A", Position(300, landScape.groundLevel), 100)),
            List(200, 400, 600).map(x => DefenceTower(s"T$x-${System.currentTimeMillis() % 1000}", Position(x, landScape.groundLevel)))
          )
          case LayoutTest_2 => (
            List(City("A", Position(350, landScape.groundLevel), 100)),
            List(100, 200, 300, 400, 500, 600).map(x => DefenceTower(s"T$x-${System.currentTimeMillis() % 1000}", Position(x, landScape.groundLevel)))
          )
        }
        val waveGenerator = attackMode.selection.item match {
          case "Standard-random"  => new StandardRandomWaveGenerator()
          case "Top drop"         => new RainWaveGenerator()
          case "Intelligent wave" => new IntelligentWaveGenerator()
          case _                  => new TestWaveGenerator(quietPeriod = 5000)
        }

        gameEngine = Some(system.actorOf(GameEngine.props(defence, cities, landScape, waveGenerator, statusKeeperProxy)))

      case bc: ButtonClicked if bc.source == showGrid   => jWarTheater.showGrid = showGrid.selected
      case bc: ButtonClicked if bc.source == showTracks => jWarTheater.showTracks = showTracks.selected
    }

    val toolbar = new BoxPanel(Orientation.Horizontal) {
      contents += new Label("Towers/City")
      contents += defenceLayout
      contents += new Label("Attack mode")
      contents += attackMode
      contents += showGrid
      contents += showTracks
      contents += buttonStart
    }
    val bp = new BorderPanel {
      add(toolbar, BorderPanel.Position.North)
      add(jWarTheater, BorderPanel.Position.Center)
    }
    contents = bp

    override def closeOperation() = {
      system.shutdown()
      System.exit(0)
    }
  }

  def generateCityAndDefence(width: Int, height: Int, distance: Int, towersPerCity: Int, towerNamePrefix: String): (List[City], List[DefenceTower]) = {
    val (cities, defence) = Range(20, width - 20, distance).foldRight((List.empty[City], List.empty[DefenceTower])) {
      (xPox, acc) =>
        val name = s"t-$towerNamePrefix-$xPox"
        if ((acc._1.size + acc._2.size) % towersPerCity == 1) {
          acc.copy(_1 = City(name, Position(xPox, height), 100) :: acc._1)
        } else {
          acc.copy(_2 = DefenceTower(name, Position(xPox, height)) :: acc._2)
        }
    }
    (cities, defence)
  }

}

