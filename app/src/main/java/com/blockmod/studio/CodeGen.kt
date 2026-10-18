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

fun modIdOf(mod: ModProject): String {
    val t = safeId(mod.name, "")
    return if (t.length >= 2) t.take(40)
    else "mod_" + mod.id.filter { it.isLetterOrDigit() }.take(6).lowercase()
}

private fun esc(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

private fun itemId(s: String): String {
    val t = s.trim().lowercase().replace(" ", "")
    return if (t.contains(':')) t else "minecraft:$t"
}

private fun num(s: String, min: Int, max: Int, def: Int): Int =
    (s.trim().toIntOrNull() ?: def).coerceIn(min, max)

// Команды внутри скрипта. В коде всегда есть переменная player.
private fun body(kids: List<ModBlock>, indent: String, depth: Int, out: MutableList<String>, api: Int) {
    val getter = if (api == 2) "getValue" else "get"
    kids.forEach { k ->
        when (k.type) {
            "say" -> out.add("${indent}player.sendSystemMessage(Component.literal(\"${esc(k.a)}\"));")
            "give" -> out.add(
                "${indent}player.addItem(new ItemStack(BuiltInRegistries.ITEM.$getter(ResourceLocation.tryParse(\"${esc(itemId(k.a))}\")), ${num(k.b, 1, 64, 1)}));"
            )
            "heal" -> out.add("${indent}player.setHealth(player.getMaxHealth());")
            "repeat" -> {
                out.add("${indent}for (int i$depth = 0; i$depth < ${num(k.a, 0, 1000, 1)}; i$depth++) {")
                body(k.kids, "$indent    ", depth + 1, out, api)
                out.add("$indent}")
            }
            "if_sneak" -> {
                out.add("${indent}if (player.isShiftKeyDown()) {")
                body(k.kids, "$indent    ", depth + 1, out, api)
                out.add("$indent}")
            }
        }
    }
}

fun generateJava(mod: ModProject, blocks: List<ModBlock>): String {
    val modId = modIdOf(mod)
    val p = profileOf(mod)
    if (p.api == 0) return stubCode(mod, modId)
    return if (mod.loader == "Forge") forgeCode(mod, modId, blocks)
    else fabricCode(mod, modId, blocks, p.api)
}

private fun stubCode(mod: ModProject, modId: String): String {
    val l = ArrayList<String>()
    l.add("// Создано BlockMod Studio (${mod.loader}, Minecraft ${mod.mcVersion})")
    l.add("// Для этой версии блоки пока не создают код. Пиши код мода сам.")
    l.add("package com.example.$modId;")
    l.add("")
    if (mod.loader == "Forge") {
        l.add("import net.minecraftforge.fml.common.Mod;")
        l.add("")
        l.add("@Mod(\"$modId\")")
        l.add("public class ModMain {")
        l.add("    public ModMain() {")
        l.add("    }")
        l.add("}")
    } else {
        l.add("import net.fabricmc.api.ModInitializer;")
        l.add("")
        l.add("public class ModMain implements ModInitializer {")
        l.add("    @Override")
        l.add("    public void onInitialize() {")
        l.add("    }")
        l.add("}")
    }
    return l.joinToString("\n")
}

private fun fabricCode(mod: ModProject, modId: String, blocks: List<ModBlock>, api: Int): String {
    val l = ArrayList<String>()
    l.add("// Создано BlockMod Studio (Fabric, Minecraft ${mod.mcVersion})")
    l.add("package com.example.$modId;")
    l.add("")
    val imports = arrayListOf(
        "net.fabricmc.api.ModInitializer",
        "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback",
        "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents",
        "net.minecraft.commands.Commands",
        "net.minecraft.core.Registry",
        "net.minecraft.core.registries.BuiltInRegistries",
        "net.minecraft.network.chat.Component",
        "net.minecraft.resources.ResourceLocation",
        "net.minecraft.server.level.ServerPlayer",
        "net.minecraft.world.item.BlockItem",
        "net.minecraft.world.item.Item",
        "net.minecraft.world.item.ItemStack",
        "net.minecraft.world.level.block.Block",
        "net.minecraft.world.level.block.state.BlockBehaviour"
    )
    if (api == 2) {
        imports.add("net.minecraft.core.registries.Registries")
        imports.add("net.minecraft.resources.ResourceKey")
    }
    imports.forEach { l.add("import $it;") }
    l.add("")
    l.add("public class ModMain implements ModInitializer {")
    l.add("    public static final String MOD_ID = \"$modId\";")
    l.add("")
    l.add("    @Override")
    l.add("    public void onInitialize() {")
    blocks.forEach { b ->
        when (b.type) {
            "item" -> {
                val id = safeId(b.a, "thing")
                if (api == 2) {
                    l.add("        ResourceLocation iid_$id = ResourceLocation.tryParse(MOD_ID + \":$id\");")
                    l.add("        Registry.register(BuiltInRegistries.ITEM, iid_$id, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, iid_$id))));")
                } else {
                    l.add("        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.tryParse(MOD_ID + \":$id\"), new Item(new Item.Properties()));")
                }
            }
            "block" -> {
                val id = safeId(b.a, "thing")
                if (api == 2) {
                    l.add("        ResourceLocation bid_$id = ResourceLocation.tryParse(MOD_ID + \":$id\");")
                    l.add("        Block blk_$id = Registry.register(BuiltInRegistries.BLOCK, bid_$id, new Block(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, bid_$id))));")
                    l.add("        Registry.register(BuiltInRegistries.ITEM, bid_$id, new BlockItem(blk_$id, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, bid_$id))));")
                } else {
                    l.add("        Block blk_$id = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.tryParse(MOD_ID + \":$id\"), new Block(BlockBehaviour.Properties.of()));")
                    l.add("        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.tryParse(MOD_ID + \":$id\"), new BlockItem(blk_$id, new Item.Properties()));")
                }
            }
            "on_command" -> {
                l.add("        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->")
                l.add("            dispatcher.register(Commands.literal(\"${safeId(b.a, "hello")}\").executes(context -> {")
                l.add("                ServerPlayer player = context.getSource().getPlayerOrException();")
                body(b.kids, "                ", 1, l, api)
                l.add("                return 1;")
                l.add("            })));")
            }
            "on_join" -> {
                l.add("        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {")
                l.add("            ServerPlayer player = ${if (api == 2) "handler.getPlayer()" else "handler.player"};")
                body(b.kids, "            ", 1, l, api)
                l.add("        });")
            }
        }
    }
    l.add("    }")
    l.add("}")
    return l.joinToString("\n")
}

private fun forgeCode(mod: ModProject, modId: String, blocks: List<ModBlock>): String {
    val l = ArrayList<String>()
    l.add("// Создано BlockMod Studio (Forge, Minecraft ${mod.mcVersion})")
    l.add("package com.example.$modId;")
    l.add("")
    listOf(
        "net.minecraft.commands.Commands",
        "net.minecraft.core.registries.BuiltInRegistries",
        "net.minecraft.network.chat.Component",
        "net.minecraft.resources.ResourceLocation",
        "net.minecraft.server.level.ServerPlayer",
        "net.minecraft.world.item.BlockItem",
        "net.minecraft.world.item.Item",
        "net.minecraft.world.item.ItemStack",
        "net.minecraft.world.level.block.Block",
        "net.minecraft.world.level.block.state.BlockBehaviour",
        "net.minecraftforge.common.MinecraftForge",
        "net.minecraftforge.event.RegisterCommandsEvent",
        "net.minecraftforge.event.entity.player.PlayerEvent",
        "net.minecraftforge.eventbus.api.IEventBus",
        "net.minecraftforge.eventbus.api.SubscribeEvent",
        "net.minecraftforge.fml.common.Mod",
        "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",
        "net.minecraftforge.registries.DeferredRegister",
        "net.minecraftforge.registries.ForgeRegistries",
        "net.minecraftforge.registries.RegistryObject"
    ).forEach { l.add("import $it;") }
    l.add("")
    l.add("@Mod(ModMain.MOD_ID)")
    l.add("public class ModMain {")
    l.add("    public static final String MOD_ID = \"$modId\";")
    l.add("    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);")
    l.add("    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);")
    l.add("")
    blocks.forEach { b ->
        val id = safeId(b.a, "thing")
        val c = id.uppercase()
        if (b.type == "item") {
            l.add("    public static final RegistryObject<Item> ITEM_$c = ITEMS.register(\"$id\", () -> new Item(new Item.Properties()));")
        }
        if (b.type == "block") {
            l.add("    public static final RegistryObject<Block> BLOCK_$c = BLOCKS.register(\"$id\", () -> new Block(BlockBehaviour.Properties.of()));")
            l.add("    public static final RegistryObject<Item> BLOCK_${c}_ITEM = ITEMS.register(\"$id\", () -> new BlockItem(BLOCK_$c.get(), new Item.Properties()));")
        }
    }
    l.add("")
    l.add("    public ModMain() {")
    l.add("        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();")
    l.add("        ITEMS.register(bus);")
    l.add("        BLOCKS.register(bus);")
    l.add("        MinecraftForge.EVENT_BUS.register(this);")
    l.add("    }")

    val commands = blocks.filter { it.type == "on_command" }
    if (commands.isNotEmpty()) {
        l.add("")
        l.add("    @SubscribeEvent")
        l.add("    public void onCommands(RegisterCommandsEvent event) {")
        commands.forEach { b ->
            l.add("        event.getDispatcher().register(Commands.literal(\"${safeId(b.a, "hello")}\").executes(context -> {")
            l.add("            ServerPlayer player = context.getSource().getPlayerOrException();")
            body(b.kids, "            ", 1, l, 1)
            l.add("            return 1;")
            l.add("        }));")
        }
        l.add("    }")
    }

    val joins = blocks.filter { it.type == "on_join" }
    if (joins.isNotEmpty()) {
        l.add("")
        l.add("    @SubscribeEvent")
        l.add("    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {")
        l.add("        if (!(event.getEntity() instanceof ServerPlayer player)) return;")
        joins.forEach { b -> body(b.kids, "        ", 1, l, 1) }
        l.add("    }")
    }
    l.add("}")
    return l.joinToString("\n")
}
