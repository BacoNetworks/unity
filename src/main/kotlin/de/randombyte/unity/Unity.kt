package de.randombyte.unity

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.getPlayer
import de.randombyte.kosp.extensions.sendTo
import de.randombyte.kosp.extensions.toText
import Helper.MarriedPrefix
import de.randombyte.unity.Unity.Companion.AUTHOR
import de.randombyte.unity.Unity.Companion.ID
import de.randombyte.unity.Unity.Companion.NAME
import de.randombyte.unity.Unity.Companion.NUCLEUS_ID
import de.randombyte.unity.Unity.Companion.VERSION
import de.randombyte.unity.commands.*
import de.randombyte.unity.config.Config
import de.randombyte.unity.config.ConfigAccessor
import Helper
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.apache.commons.lang3.RandomUtils
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.args.GenericArguments.player
import org.spongepowered.api.command.spec.CommandSpec
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.data.key.Keys
import org.spongepowered.api.data.property.entity.EyeLocationProperty
import org.spongepowered.api.effect.particle.ParticleEffect
import org.spongepowered.api.effect.particle.ParticleTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.entity.InteractEntityEvent
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.filter.cause.Root
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.event.game.state.GameStartedServerEvent
import org.spongepowered.api.event.game.state.GameStartingServerEvent
import org.spongepowered.api.event.network.ClientConnectionEvent
import org.spongepowered.api.plugin.Dependency
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.scheduler.Task
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Plugin(id = ID,
        name = NAME,
        version = VERSION,
        authors = [AUTHOR],
        dependencies = [(Dependency(id = NUCLEUS_ID, optional = true))])
class Unity @Inject constructor(
        private val logger: Logger,
        @DefaultConfig(sharedRoot = true) configurationLoader: ConfigurationLoader<CommentedConfigurationNode>,
        public val pluginContainer: PluginContainer
) {
    companion object {
        const val ID = "unity"
        const val NAME = "Unity"
        const val VERSION = "2.3.2"
        const val AUTHOR = "RandomByte"


        const val NUCLEUS_ID = "nucleus"

        const val ROOT_PERMISSION = ID
        const val PLAYER_PERMISSION = "$ROOT_PERMISSION.player"

        const val PLAYER_ARG = "player"

        val dateOutputFormat = SimpleDateFormat("dd.MM.yyyy")
    }

    private val configManager = ConfigManager(
            configLoader = configurationLoader,
            clazz = Config::class.java,
            simpleTextSerialization = true,
            simpleTextTemplateSerialization = true
    )

    private lateinit var config: Config

    private val configAccessor = object : ConfigAccessor() {
        override fun get() = config
        override fun set(config: Config) {
            this@Unity.config = config
            saveConfig() // always save config in case of sudden server shutdown
        }
    }

    // <requestee, requesters>
    private val unityRequests: MutableMap<UUID, List<UUID>> = mutableMapOf()

    private val kissingParticleEffect = lazy {
        ParticleEffect.builder()
                .type(ParticleTypes.HEART)
                .quantity(1)
                .offset(Vector3d(0.3, 0.3, 0.3))
                .velocity(Vector3d(0.1, 0.1, 0.0))
                .build()
    }

    @Listener
    fun onInit(event: GameInitializationEvent) {
        registerCommands()

        logger.info("Loaded $NAME: $VERSION")
    }

    @Listener
    fun onWorldsLoaded(event: GameStartingServerEvent) {
        // do this here to ensure all worlds are loaded for location deserialization
        loadConfig()
        saveConfig()
        Helper.container = this.pluginContainer

        if (needsMotivationalSpeech()) {
            Task.builder()
                    .delay(RandomUtils.nextLong(80, 130), TimeUnit.SECONDS)
                    .execute { -> Messages.motivationalSpeech.forEach { it.sendTo(Sponge.getServer().console) } }
                    .submit(this)
        }
    }

    @Listener
    fun onServerStarted(event: GameStartedServerEvent) {
        logger.info("Loading " + config.unities.size + " active marriages!")
        Helper.FillHashMap(config.unities)
        MarriedPrefix = config.marriedPrefix;
        Helper.RegisterNucleusToken();
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        loadConfig()
        saveConfig()
        unityRequests.clear()

        logger.info("Reloaded!")
    }


    @Listener
    fun onKissPartner(event: InteractEntityEvent.Secondary.MainHand, @Root player: Player, @Getter("getTargetEntity") partner: Player) {
        if (!Helper.isKissingEnabled || !player.get(Keys.IS_SNEAKING).orElse(false)) {
            return
        }
        val unity = Helper.MarriedMap[player.uniqueId] ?: return;
        val unity2 = Helper.MarriedMap[partner.uniqueId] ?: return;

        if (unity != unity2) {
            return
        }
        partner.world.spawnParticles(kissingParticleEffect.value, partner.getProperty(EyeLocationProperty::class.java).get().value)
    }


    private fun registerCommands() {
        val removeRequest = { requester: UUID, requestee: UUID ->
            unityRequests += (requestee to (unityRequests[requestee] ?: emptyList()).filterNot { it == requester })
        }

        Sponge.getCommandManager().register(this, CommandSpec.builder()
                // the root unity/marry command is the 'request unity'-command
                .permission("$PLAYER_PERMISSION.marry")
                .arguments(player(PLAYER_ARG.toText()))
                .executor(RequestUnityCommand(
                        configAccessor,
                        addRequest = { requester, requestee ->
                            val existingRequesters = unityRequests[requestee] ?: emptyList()
                            if (requester in existingRequesters) return@RequestUnityCommand false
                            unityRequests += (requestee to (existingRequesters + requester))
                            return@RequestUnityCommand true
                        }
                ))

                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.help")
                        .executor(HelpCommand(configAccessor))
                        .build(), "help")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.accept")
                        .arguments(player(PLAYER_ARG.toText()))
                        .executor(AcceptRequestCommand(configAccessor, this::unityRequests, removeRequest))
                        .build(), "accept")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.decline")
                        .arguments(player(PLAYER_ARG.toText()))
                        .executor(DeclineRequestCommand(configAccessor, this::unityRequests, removeRequest))
                        .build(), "decline")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.cancel")
                        .arguments(player(PLAYER_ARG.toText()))
                        .executor(CancelRequestCommand(configAccessor, this::unityRequests, removeRequest))
                        .build(), "cancel")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.list")
                        .executor(ListUnitiesCommand(configAccessor))
                        .build(), "list")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.divorce")
                        .executor(DivorceCommand(configAccessor))
                        .build(), "divorce")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.tp")
                        .executor(TeleportCommand(configAccessor))
                        .build(), "teleport", "tp")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.gift")
                        .executor(GiftCommand(configAccessor))
                        .build(), "gift")
                .child(CommandSpec.builder()
                        .permission("$PLAYER_PERMISSION.home.home")
                        .executor(HomeCommand(configAccessor))
                        .child(CommandSpec.builder()
                                .permission("$PLAYER_PERMISSION.home.set")
                                .executor(SetHomeCommand(configAccessor))
                                .build(), "set")
                        .build(), "home")
                .build(), "unity", "marry")
    }

    private fun loadConfig() {
        config = configManager.get()
    }

    private fun saveConfig() {
        configManager.save(config)
    }


    val metricsNoteSent = mutableSetOf<UUID>()

    @Listener
    fun onPlayerJoin(event: ClientConnectionEvent.Join) {
        val uuid = event.targetEntity.uniqueId
        if (needsMotivationalSpeech(event.targetEntity)) {
            Task.builder()
                    .delay(RandomUtils.nextLong(10, 50), TimeUnit.SECONDS)
                    .execute { ->
                        val player = uuid.getPlayer() ?: return@execute
                        metricsNoteSent += uuid
                        Messages.motivationalSpeech.forEach { it.sendTo(player) }
                    }
                    .submit(this)
        }
    }

    private fun needsMotivationalSpeech(player: Player? = null) = config.enableMetricsMessages &&
            !Sponge.getMetricsConfigManager().areMetricsEnabled(this) &&
            ((player == null) || player.uniqueId !in metricsNoteSent && player.hasPermission("nucleus.mute.base")) // also passes OPs without Nucleus
}