package defend

import scala.util.parsing.combinator.RegexParsers

trait Terminal {

  sealed trait Command

  object Command {

    case object Shutdown extends Command
    case object Leave extends Command

    case class Unknown(command: String, message: String) extends Command

    def apply(command: String, parser: CommandParser.Parser[Command]): Command =
      CommandParser.parseAsCommand(command, parser)
  }

  object CommandParser extends RegexParsers {

    def parseAsCommand(s: String, parser: CommandParser.Parser[Command]): Command = {
      parseAll(parser, s) match {
        case Success(command, _)   => command
        case NoSuccess(message, _) => Command.Unknown(s, message)
      }
    }

    def shutdown: Parser[Command] = "shutdown".r ^^ (_ => Command.Shutdown)
    def leave: Parser[Command] = "leave".r ^^ (_ => Command.Leave)

  }
}
