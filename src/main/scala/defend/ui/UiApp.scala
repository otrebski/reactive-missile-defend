package defend.ui

import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory

import akka.actor._
import com.typesafe.scalalogging.slf4j.LazyLogging
import defend.PersistenceMonitor
import defend.cluster._
import defend.game._
import defend.model._
import defend.ui.JWarTheater.CommandCenterPopupAction
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
import scala.swing._
import scala.swing.event.{ ButtonClicked, ValueChanged }

object UiApp extends SimpleSwingApplication
    with SharedLevelDb
    with DefendActorSystem
    with StatusKeeperSingleton
    with StatusKeeperProxy
    with TowerShardProxy {

  import pl.project13.scala.rainbow.Rainbow._

  println("Starting".green)

  system.actorOf(Props(new PersistenceMonitor(statusKeeperProxy, System.currentTimeMillis)))
  val manualShotDamageSlider = new scala.swing.Slider() {
    orientation = Orientation.Horizontal
    max = 100
    min = 0
    minorTickSpacing = 5
    majorTickSpacing = 10
    paintTicks = true
    paintTrack = true
    paintLabels = true
    snapToTicks = true
    border = BorderFactory.createTitledBorder("Manual shoot damage")
  }
  private val duration: Long = config.as[FiniteDuration]("akka.cluster.auto-down-unreachable-after").toMillis
  private val emptyWarTheater: WarTheater = WarTheater(List.empty, List.empty, List.empty, List.empty, LandScape(500, 500, 120), List.empty)
  private var gameEngine: Option[ActorRef] = None
  private val dragFunction = new ((DragEvent) => Unit) {
    override def apply(v1: DragEvent): Unit = {
      gameEngine.filter(_ => v1.moveVector.speed > 10)
        .foreach(_ ! GameEngine.Protocol.AlienRocketFired(AlienMissile(manualShotDamageSlider.value, 30), v1.start, v1.moveVector, None))
    }
  }

  private val terminateActorSystem = new CommandCenterPopupAction("Terminate actor system", new (String => Unit) {
    override def apply(v1: String): Unit = {
      val path: String = s"$v1/user/shutdown"
      println(s"Will send shutdown to path $path")
      system.actorSelection(path) ! ShutdownNode.Terminate
    }
  })

  private val leaveCluster = new CommandCenterPopupAction("Leave cluster", new (String => Unit) {
    override def apply(v1: String): Unit = {
      val path: String = s"$v1/user/shutdown"
      println(s"Will send shutdown to path $path")
      system.actorSelection(path) ! ShutdownNode.LeaveCluster
    }
  })

  private val systemExit0 = new CommandCenterPopupAction("Call system exit(0)", new (String => Unit) {
    override def apply(v1: String): Unit = {
      val path: String = s"$v1/user/shutdown"
      println(s"Will send shutdown to path $path")
      system.actorSelection(path) ! ShutdownNode.SystemExit0
    }
  })

  private val jWarTheater: JWarTheater = new JWarTheater(emptyWarTheater, duration,
    showGrid                 = false,
    showTracks               = true,
    dragListener             = Some(dragFunction),
    commandCenterPopupAction = List(terminateActorSystem, leaveCluster, systemExit0))

  private val uiUpdater = system.actorOf(UiUpdater.props(jWarTheater, statusKeeperProxy), "uiUpdater")

  override def top: Frame = new MainFrame with LazyLogging {
    system.registerOnTermination(logger.info("Terminating actor system"))

    title = "Reactive Missile Defend  UI"

    private val buttonStart = new Button {
      text = "Start game"
    }

    private val LayoutStandard: String = "Standard"
    private val LayoutLow: String = "Low defence"
    private val LayoutHigh: String = "High defence"
    private val LayoutTest: String = "test"
    private val LayoutTest_2: String = "test-2"
    private val LayoutNarrow: String = "Narrow"

    private val defenceLayout = new ComboBox[String](List(
      LayoutStandard,
      LayoutLow,
      LayoutHigh,
      LayoutTest,
      LayoutTest_2,
      LayoutNarrow
    ))
    private val attackMode = new ComboBox[String](List("Random", "Top drop", "Intelligent wave", "Text wave", "Test"))
    private val showGrid = new CheckBox("Show grid")
    showGrid.selected = jWarTheater.showGrid
    private val showTracks = new CheckBox("Show tracks")
    showTracks.selected = jWarTheater.showTracks

    private val statusLabel: Label = new Label("")
    private var isOver: Boolean = true

    val delaySlider = new scala.swing.Slider() {
      orientation = Orientation.Horizontal
      max = 100
      min = 10
      //      minorTickSpacing = 10
      majorTickSpacing = 10
      //      paintTicks = true
      paintTrack = true
      paintLabels = true
      snapToTicks = true
      border = BorderFactory.createTitledBorder("Sleep time [ms]")
    }

    listenTo(buttonStart)
    listenTo(showGrid)
    listenTo(showTracks)
    listenTo(delaySlider)

    val f: () => Unit = { () => Swing.onEDT(gameFinished()) }

    reactions += {
      case bc: ButtonClicked if bc.source == buttonStart =>
        val size1: Dimension = jWarTheater.size
        val landScape = LandScape(size.width, size1.height, 150)
        gameEngine.foreach(_ ! PoisonPill)
        val towerNamePrefix = new SimpleDateFormat("dd_HHmmss").format(new Date())
        //        val (cities, defence) = generateCityAndDefence(50, landScape.groundLevel, 30, towersPerCity)
        val (cities, defence) = defenceLayout.selection.item match {
          case LayoutStandard => generateCityAndDefence(landScape.width, landScape.groundLevel, 30, 4, towerNamePrefix)
          case LayoutLow      => generateCityAndDefence(landScape.width, landScape.groundLevel, 50, 2, towerNamePrefix)
          case "High defence" => generateCityAndDefence(landScape.width, landScape.groundLevel, 20, 6, towerNamePrefix)
          case LayoutTest => (
            List(City("A", Position(300, landScape.groundLevel), 100)),
            List(200, 400, 600).map(x => DefenceTower(s"T$x-${System.currentTimeMillis() % 1000}", Position(x, landScape.groundLevel)))
          )
          case LayoutTest_2 => (
            List(City("A", Position(350, landScape.groundLevel), 100)),
            List(100, 200, 300, 400, 500, 600).map(x => DefenceTower(s"T$x-${System.currentTimeMillis() % 1000}", Position(x, landScape.groundLevel)))
          )
          case LayoutNarrow => (
            List(City("A", Position(110, landScape.groundLevel), 100)),
            List(20, 50, 80, 140, 170).map(x => DefenceTower(s"T$x-${System.currentTimeMillis() % 1000}", Position(x, landScape.groundLevel)))
          )
        }
        val waveGenerator = attackMode.selection.item match {
          case "Random"           => new StandardRandomWaveGenerator()
          case "Top drop"         => new RainWaveGenerator()
          case "Intelligent wave" => new IntelligentWaveGenerator()
          case "Text wave"        => new AdWaveGenerator()
          case _                  => new TestWaveGenerator(quietPeriod = 5000)
        }
        import scala.concurrent.duration._
        val delayDuration: FiniteDuration = FiniteDuration(delaySlider.value.toLong, scala.concurrent.duration.MILLISECONDS)
        gameEngine = Some(system.actorOf(GameEngine.props(defence, cities, landScape, waveGenerator, statusKeeperProxy, Some(f), delayDuration)))
        isOver = false

      case bc: ButtonClicked if bc.source == showGrid   => jWarTheater.showGrid = showGrid.selected
      case bc: ButtonClicked if bc.source == showTracks => jWarTheater.showTracks = showTracks.selected
      case vc: ValueChanged if vc.source == delaySlider =>
        val duration: FiniteDuration = FiniteDuration(delaySlider.value.toLong, scala.concurrent.duration.MILLISECONDS)
        gameEngine.foreach(_ ! GameEngine.Protocol.UpdateDelayTime(duration))
    }

    val toolbar1 = new BoxPanel(Orientation.Horizontal) {
      contents += new Label("Towers/City")
      contents += defenceLayout
      contents += new Label("Attack mode")
      contents += attackMode
    }
    val toolbar2 = new BoxPanel(Orientation.Horizontal) {
      contents += showGrid
      contents += showTracks
      contents += buttonStart
    }
    val toolbar = new BoxPanel(Orientation.Vertical) {
      contents += toolbar1
      contents += toolbar2
    }

    val toolbarSouth = new BoxPanel(Orientation.Horizontal) {
      contents += statusLabel
    }
    val bp = new BorderPanel {
      add(toolbar, BorderPanel.Position.North)
      add(jWarTheater, BorderPanel.Position.Center)
      add(toolbarSouth, BorderPanel.Position.South)
    }

    private val settingsPanel = new BoxPanel(Orientation.Vertical) {
      contents += delaySlider
      contents += manualShotDamageSlider
    }

    val tb = new TabbedPane {
      pages += new TabbedPane.Page("Game", bp)
      pages += new TabbedPane.Page("Settings", settingsPanel)
    }

    contents = tb

    override def closeOperation() = {
      system.terminate()
      System.exit(0)
    }

    def gameFinished(): Unit = {
      println("Game finished")
      isOver = true
      implicit val ec = ExecutionContext.global
      Future {
        (0 until 10).reverse.foreach { i =>
          Thread.sleep(1000)
          Swing.onEDT(statusLabel.text = s"Restart int ${i}s")
        }
        Swing.onEDT(restartIfOver())
      }
    }

    def restartIfOver(): Unit = {
      println(s"Restarting if game is over $isOver")
      if (isOver) {
        statusLabel.text = "Restarting"
        buttonStart.doClick()
      }
    }

    override def close(): Unit = {
      logger.info("Closing UIApp window")
      super.close()
    }

    size = new Dimension(800, 600)
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

