package defend.ui

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import defend.model.CommandCenter

object CommandCenterIcons {

  lazy val commandCenterIcons = """abacus.png
                             |animal-dog.png
                             |animal-monkey.png
                             |ghost.png
                             |animal-penguin.png
                             |animal.png
                             |bean-green.png
                             |computer.png
                             |cutlery-fork.png
                             |cutlery-knife.png
                             |cutlery-spoon.png
                             |drill.png
                             |fire.png
                             |flag-checker.png
                             |fruit-apple-half.png
                             |fruit-grape.png
                             |fruit-lime.png
                             |fruit-orange.png
                             |fruit.png
                             |ice-cream-blue-moon.png
                             |eye.png
                             |mask.png
                             |luggage.png
                             |milk-label.png
                             |money-bag.png
                             |light-bulb.png
                             |bell.png
                             |mouse.png
                             |occluder.png
                             |rubber-balloon.png
                             |paper-plane.png
                             |piggy-bank-empty.png
                             |pill-blue.png
                             |pill.png
                             |quill.png
                             |sport-basketball.png
                             |sport-golf.png
                             |sushi.png
                             |train.png""".stripMargin
    .split("\n")
    .map(_.trim)
    .toList
    .map(x => ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("cc/" + x)))

  lazy val leaderIcon = ImageIO.read(this.getClass.getClassLoader.getResourceAsStream("icons/new.png"))

  lazy val iconsForPort = Map(
    "3000" -> "database.png",
    "3001" -> "android.png",
    "3002" -> "cup.png",
    "3003" -> "magnet.png",
    "3004" -> "diamond.png",
    "3005" -> "hammer.png",
    "3006" -> "magnet.png",
    "3007" -> "piano.png",
    "3008" -> "ruby.png",
    "3009" -> "snowman-hat.png"
  ).map(x => {
      val name: String = "cc-by-port/" + x._2
      (x._1, ImageIO.read(this.getClass.getClassLoader.getResourceAsStream(name)))
    })

  lazy val iconsForIp = Map(
    "192.168.0.10" -> "cup.png",
    "192.168.0.11" -> "diamond.png",
    "192.168.0.12" -> "ruby.png",
    "192.168.0.13" -> "magnet.png",
    "192.168.1.10" -> "cup.png",
    "192.168.1.11" -> "diamond.png",
    "192.168.1.12" -> "ruby.png",
    "192.168.1.13" -> "magnet.png",
    "192.168.2.10" -> "cup.png",
    "192.168.2.11" -> "diamond.png",
    "192.168.2.12" -> "ruby.png",
    "192.168.2.13" -> "magnet.png"
  ).map(x => {
      val name: String = "cc-by-port/" + x._2
      (x._1, ImageIO.read(this.getClass.getClassLoader.getResourceAsStream(name)))
    })

  def iconForCommandCentre(commandCentre: String): BufferedImage = {
    //akka.tcp://defend@127.0.0.1:3000
    def defaultName(name: String) = {
      commandCenterIcons(Math.abs(commandCentre.hashCode) % commandCenterIcons.size)
    }
    val regex = "akka.tcp://.*@(.*):(\\d+)".r
    commandCentre match {
      case regex(ip, port) if iconsForIp.contains(ip) => iconsForIp.getOrElse(ip, defaultName(commandCentre))
      case regex(ip, port)                            => iconsForPort.getOrElse(port, defaultName(commandCentre))
      case _                                          => defaultName(commandCentre)
    }
  }

  def iconForCommandCentre(commandCentre: CommandCenter): BufferedImage = {
    iconForCommandCentre(commandCentre.name)
  }

  def main(args: Array[String]) {
    val centre1: BufferedImage = iconForCommandCentre("akka.tcp://defend@127.0.0.1:3000")
    val centre2: BufferedImage = iconForCommandCentre("akka.tcp://defend@127.0.0.1:3000")
  }
}

