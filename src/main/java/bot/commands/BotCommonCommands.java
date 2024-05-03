package bot.commands;

public class BotCommonCommands {
    public BotCommonCommands() {}
    @AppBotCommand(name = "/Hello", description = "when request hello", showInHelp = true)
    String hello() {
        return "Hello, User";
    }
    @AppBotCommand(name = "/Bye", description = "when request bye", showInHelp = true)
    String bye() {
        return "Good bye, User";
    }
    @AppBotCommand(name = "/Help", description = "when request help ", showInKeyboard = true)
    String help() {
        return "Heeelp";
    }
}
