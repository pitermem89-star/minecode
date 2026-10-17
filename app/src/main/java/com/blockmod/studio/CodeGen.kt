package com.blockmod.studio

fun safeId(s: String, fallback: String): String {
    val t = s.lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9' || it == '_') it else '_' }
        .joinToString("")
        .trim('_')
    return when {
        t.isEmpty() -> fallback
        t[0].isDigit() -> "x_$t"
        else -> t
    }
}

private fun esc(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

fun generateJava(mod: ModProject, blocks: List<ModBlock>): String {
    val modId = safeId(mod.name, "mymod")
    return if (mod.loader == "Forge") forgeCode(mod, modId, blocks) else fabricCode(mod, modId, blocks)
}

private fun fabricCode(mod: ModProject, modId: String, blocks: List<ModBlock>): String {
    val l = ArrayList<String>()
    l += "// Fabric, Minecraft ${mod.mcVersion}."
    l += "// В разных версиях Minecraft названия некоторых классов отличаются."
    l += "package com.example.$modId;"
    l += ""
    l += "import net.fabricmc.api.ModInitializer;"
    l += "import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;"
    l += "import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;"
    l += "import net.minecraft.block.AbstractBlock;"
    l += "import net.minecraft.block.Block;"
    l += "import net.minecraft.item.BlockItem;"
    l += "import net.minecraft.item.Item;"
    l += "import net.minecraft.registry.Registries;"
    l += "import net.minecraft.registry.Registry;"
    l += "import net.minecraft.server.command.CommandManager;"
    l += "import net.minecraft.text.Text;"
    l += "import net.minecraft.util.Identifier;"
    l += ""
    l += "public class ModMain implements ModInitializer {"
    l += "    public static final String MOD_ID = \"$modId\";"
    l += ""
    l += "    @Override"
    l += "    public void onInitialize() {"
    blocks.forEach { b ->
        when (b.type) {
            "item" -> {
                val id = safeId(b.a, "thing")
                l += "        // Предмет: ${esc(b.b)}"
                l += "        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, \"$id\"), new Item(new Item.Settings()));"
            }
            "block" -> {
                val id = safeId(b.a, "thing")
                l += "        // Блок: ${esc(b.b)}"
                l += "        Block ${id}Block = Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, \"$id\"), new Block(AbstractBlock.Settings.create()));"
                l += "        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, \"$id\"), new BlockItem(${id}Block, new Item.Settings()));"
            }
            "command" -> {
                val name = safeId(b.a, "hello")
                l += "        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->"
                l += "            dispatcher.register(CommandManager.literal(\"$name\").executes(context -> {"
                l += "                context.getSource().sendFeedback(() -> Text.literal(\"${esc(b.b)}\"), false);"
                l += "                return 1;"
                l += "            })));"
            }
            "join" -> {
                l += "        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->"
                l += "            handler.player.sendMessage(Text.literal(\"${esc(b.a)}\"), false));"
            }
        }
    }
    l += "    }"
    l += "}"
    return l.joinToString("\n")
}

private fun forgeCode(mod: ModProject, modId: String, blocks: List<ModBlock>): String {
    val l = ArrayList<String>()
    l += "// Forge, Minecraft ${mod.mcVersion}."
    l += "// В разных версиях Minecraft названия некоторых классов отличаются."
    l += "package com.example.$modId;"
    l += ""
    l += "import net.minecraft.commands.Commands;"
    l += "import net.minecraft.network.chat.Component;"
    l += "import net.minecraft.world.item.BlockItem;"
    l += "import net.minecraft.world.item.Item;"
    l += "import net.minecraft.world.level.block.Block;"
    l += "import net.minecraft.world.level.block.state.BlockBehaviour;"
    l += "import net.minecraftforge.common.MinecraftForge;"
    l += "import net.minecraftforge.event.RegisterCommandsEvent;"
    l += "import net.minecraftforge.event.entity.player.PlayerEvent;"
    l += "import net.minecraftforge.eventbus.api.IEventBus;"
    l += "import net.minecraftforge.eventbus.api.SubscribeEvent;"
    l += "import net.minecraftforge.fml.common.Mod;"
    l += "import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;"
    l += "import net.minecraftforge.registries.DeferredRegister;"
    l += "import net.minecraftforge.registries.ForgeRegistries;"
    l += "import net.minecraftforge.registries.RegistryObject;"
    l += ""
    l += "@Mod(ModMain.MOD_ID)"
    l += "public class ModMain {"
    l += "    public static final String MOD_ID = \"$modId\";"
    l += "    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);"
    l += "    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);"
    l += ""
    blocks.forEach { b ->
        val id = safeId(b.a, "thing")
        val c = id.uppercase()
        when (b.type) {
            "item" -> {
                l += "    // Предмет: ${esc(b.b)}"
                l += "    public static final RegistryObject<Item> $c = ITEMS.register(\"$id\", () -> new Item(new Item.Properties()));"
            }
            "block" -> {
                l += "    // Блок: ${esc(b.b)}"
                l += "    public static final RegistryObject<Block> $c = BLOCKS.register(\"$id\", () -> new Block(BlockBehaviour.Properties.of()));"
                l += "    public static final RegistryObject<Item> ${c}_ITEM = ITEMS.register(\"$id\", () -> new BlockItem($c.get(), new Item.Properties()));"
            }
        }
    }
    l += ""
    l += "    public ModMain() {"
    l += "        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();"
    l += "        ITEMS.register(bus);"
    l += "        BLOCKS.register(bus);"
    l += "        MinecraftForge.EVENT_BUS.register(this);"
    l += "    }"
    val commands = blocks.filter { it.type == "command" }
    if (commands.isNotEmpty()) {
        l += ""
        l += "    @SubscribeEvent"
        l += "    public void onCommands(RegisterCommandsEvent event) {"
        commands.forEach { b ->
            l += "        event.getDispatcher().register(Commands.literal(\"${safeId(b.a, "hello")}\").executes(context -> {"
            l += "            context.getSource().sendSuccess(() -> Component.literal(\"${esc(b.b)}\"), false);"
            l += "            return 1;"
            l += "        }));"
        }
        l += "    }"
    }
    val joins = blocks.filter { it.type == "join" }
    if (joins.isNotEmpty()) {
        l += ""
        l += "    @SubscribeEvent"
        l += "    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {"
        joins.forEach { b ->
            l += "        event.getEntity().sendSystemMessage(Component.literal(\"${esc(b.a)}\"));"
        }
        l += "    }"
    }
    l += "}"
    return l.joinToString("\n")
}
