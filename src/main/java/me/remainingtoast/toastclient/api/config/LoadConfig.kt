package me.remainingtoast.toastclient.api.config

import kotlinx.serialization.decodeFromString
import me.remainingtoast.toastclient.ToastClient
import me.remainingtoast.toastclient.api.module.Module
import me.remainingtoast.toastclient.api.module.ModuleManager
import me.remainingtoast.toastclient.api.setting.Setting.*
import me.remainingtoast.toastclient.client.module.client.ClickGUIModule
import me.remainingtoast.toastclient.client.module.client.HUDEditor
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


/**
 * Original @author Hoosiers
 * @since 10/15/2020
 * Rewritten into Fabric/Kotlin @author RemainingToast
 * Rewritten to use kotlinx.serialization instead of gson @author Vonr/Qther, only method logic from Hoosiers remains
 * @since 01/02/2021
 * @see https://github.com/IUDevman/gamesense-client/blob/master/src/main/java/com/gamesense/api/config/LoadConfig.java
 **/
object LoadConfig {

    val mainDirectory = "${MinecraftClient.getInstance().runDirectory.canonicalPath}/toastclient/"
    val moduleDirectory = "modules/"

    fun init(){
        try {
            loadConfig()
        } catch (e: IOException){
            e.printStackTrace()
        }
    }

    fun loadConfig(){
        loadModules()
    }

    fun loadModules() {
        val moduleLocation: String = mainDirectory + moduleDirectory
        for (module in ModuleManager.modules) {
            try {
                loadModuleDirect(moduleLocation, module)
            } catch (e: IOException) {
                println(module.name)
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    fun loadModuleDirect(moduleLocation: String, module: Module) {
        val fileLoc = moduleLocation + module.name + ".json"
        if (!Files.exists(Paths.get(fileLoc))) {
            return
        }
        val reader = BufferedReader(FileReader(fileLoc))
        val text = reader.readText()
        reader.close()
        if (text.isNotEmpty()) {
            val decodedModule = ToastClient.JSON.decodeFromString<Module>(text)
//            module.settings = decodedModule.settings
            for (setting in module.settings) {
                for (decodedSetting in decodedModule.settings) {
                    if (setting.name != decodedSetting.name) continue
                    when (setting) {
                        is BooleanSetting -> {
                            setting.value = (decodedSetting as BooleanSetting).value
                        }
                        is KeybindSetting -> {
                            setting.value = (decodedSetting as KeybindSetting).value
                        }
                        is IntegerSetting -> {
                            setting.value = MathHelper.clamp((decodedSetting as IntegerSetting).value, setting.min, setting.max)
                        }
                        is DoubleSetting -> {
                            setting.value = MathHelper.clamp((decodedSetting as DoubleSetting).value, setting.min, setting.max)
                        }
                        is ListSetting -> {
                            var index = (decodedSetting as ListSetting).index
                            val size = setting.list.size
                            while (size < index) index -= size
                            setting.index = index
                        }
                        is ColorSetting -> {
                            val typedSetting = decodedSetting as ColorSetting
                            setting.value = typedSetting.value
                            setting.alphaEnabled = typedSetting.alphaEnabled
                            setting.rainbow = typedSetting.rainbow
                            setting.rainbowEnabled = typedSetting.rainbowEnabled
                        }
                    }
                    break
                }
            }
            module.setBind(decodedModule.getBind())
            module.setDrawn(decodedModule.isDrawn())
            module.setEnabled(module != ClickGUIModule && module != HUDEditor && decodedModule.isEnabled())
        }
    }
}