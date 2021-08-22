package de.randombyte.unity.commands

import de.randombyte.unity.config.ConfigAccessor
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor

class ListUnitiesCommand(
        val configAccessor: ConfigAccessor
) : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        val config = configAccessor.get()
        Helper.SendList(src, config.texts.listCommandTitle)
        return CommandResult.success()
    }
}