package unprotesting.com.github.commands.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import unprotesting.com.github.AutoTune;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {
    private final AutoTune plugin;
    private final Map<String, ICommand> commands = new HashMap<>();

    public CommandManager(AutoTune plugin) {
        this.plugin = plugin;
    }

    public void registerCommand(ICommand command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        ICommand command = commands.get(cmd.getName().toLowerCase());
        if (command != null) {
            return command.execute(sender, args);
        }
        sender.sendMessage("[DEBUG] Command not found in CommandManager: " + cmd.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        ICommand command = commands.get(cmd.getName().toLowerCase());
        if (command != null) {
            return command.onTabComplete(sender, args);
        }
        return new ArrayList<>();
    }
}
