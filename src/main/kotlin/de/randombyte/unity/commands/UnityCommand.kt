package de.randombyte.unity.commands

import de.randombyte.kosp.PlayerExecutedCommand
import de.randombyte.kosp.extensions.toText
import Helper
import de.randombyte.unity.config.Config
import de.randombyte.unity.config.ConfigAccessor
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.entity.living.player.Player

abstract class UnityCommand(
        val configAccessor: ConfigAccessor
) : PlayerExecutedCommand() {
    override fun executedByPlayer(player: Player, args: CommandContext): CommandResult {
        val config = configAccessor.get()
        val unity = Helper.MarriedMap[player.uniqueId] ?: throw CommandException("You must be married to someone to execute this command!".toText());
        return executedByUnityMember(player, args, unity, config)
    }

    abstract fun executedByUnityMember(player: Player, args: CommandContext, thisUnity: Config.Unity, config: Config): CommandResult
}