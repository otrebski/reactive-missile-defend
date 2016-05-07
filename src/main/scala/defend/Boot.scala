package defend

import defend.ui.{ UiApp, CliUi }

object Boot extends App {

  def printUsage() = {
    println(
      """Need to pass 1 arguments - what type of node you want to run:
        | ui - Swing UI
        | cliui - command line UI
        | sj - shared journal
        | cc - Command center -> node
      """.stripMargin
    )
  }

  args.toList match {
    case Nil =>
      println("No args!")
      printUsage()

    case "cc" :: Nil =>
      println(s"Starting cc")
      DefenceCommandCenter.main(args)

    case "cliui" :: Nil =>
      println(s"Starting cliui")
      CliUi.main(args)

    case "ui" :: Nil =>
      println(s"Starting ui")
      UiApp.main(args)

    case command :: Nil =>
      println(s"Unknown command: $command")
      printUsage()

    case a: List[String] =>
      println("To many args")
      printUsage()
  }

}
