package com.firstt175.deepdrop.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.firstt175.deepdrop.prefs.LsfgPreferences
import com.firstt175.deepdrop.session.NativeBridge
import com.firstt175.deepdrop.ui.components.LsfgCard
import com.firstt175.deepdrop.ui.components.LsfgTopBar
import com.firstt175.deepdrop.ui.components.SectionHeader
import java.io.File

/**
 * vkBasalt settings boundary.
 *
 * The effect implementation remains the vendored vkBasalt source. This screen only
 * selects the effect chain and manages user supplied ReShade .fx files.
 */
@Composable
fun ImageEnhancementScreen(nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { LsfgPreferences(context) }
    val initial = remember { prefs.load() }

    var enabled by remember { mutableStateOf(initial.vkBasaltEnabled) }
    var effects by remember { mutableStateOf(initial.vkBasaltEffects) }
    var fxDir by remember { mutableStateOf(initial.vkBasaltFxDirectory) }

    val fxImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = File(context.filesDir, "vkbasalt/reshade")
        dir.mkdirs()
        val name = (uri.lastPathSegment ?: "effect.fx").substringAfterLast('/').let {
            if (it.endsWith(".fx", true)) it else "$it.fx"
        }
        val out = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        fxDir = dir.absolutePath
        prefs.setVkBasaltFxDirectory(fxDir)
        if (!effects.contains(name.removeSuffix(".fx"))) {
            effects = effects + name.removeSuffix(".fx")
            prefs.setVkBasaltEffects(effects)
        }
    }

    fun writeConfig() {
        val configPath = File(context.filesDir, "vkbasalt/vkBasalt.conf")
        configPath.parentFile?.mkdirs()
        val ok = runCatching {
            NativeBridge.writeVkBasaltConfig(
                configPath.absolutePath,
                enabled,
                effects.toTypedArray(),
                File(context.filesDir, "vkbasalt/reshade").absolutePath,
                File(context.filesDir, "vkbasalt/reshade").absolutePath,
                false,
                0.4f, 0.5f, 0.17f,
                0.75f, 0.125f, 0.0312f,
                0.05f, 32, 16, 25, ""
            )
        }.getOrDefault(false)
        if (ok) {
            prefs.setVkBasaltEnabled(enabled)
            prefs.setVkBasaltEffects(effects)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LsfgTopBar(title = "vkBasalt Post Processing", onBack = { nav.popBackStack() })

        LsfgCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("VKbasalt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Uses the vendored vkBasalt effect/config implementation. DeepDrop only supplies the connection and settings layer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it; writeConfig() })
            }
        }

        LsfgCard {
            SectionHeader(eyebrow = "BUILT-IN VKBASALT EFFECTS", title = null)
            val builtIns = listOf("cas", "dls", "fxaa", "smaa", "deband", "lut")
            builtIns.forEach { effect ->
                FilterChip(
                    selected = effects.contains(effect),
                    onClick = {
                        effects = if (effects.contains(effect)) effects - effect else effects + effect
                        prefs.setVkBasaltEffects(effects)
                        writeConfig()
                    },
                    label = { Text(effect.uppercase()) },
                    modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
                )
            }
            Text(
                "Order is the order in this list of enabled effects. The same effect may be selected repeatedly through the native config API when a custom config is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LsfgCard {
            SectionHeader(eyebrow = "RESHADE .FX", title = null)
            Button(onClick = { fxImport.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import .fx")
            }
            Text(
                "Imported shaders are stored under app-private vkBasalt/reshade storage and are referenced by the vkBasalt configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (effects.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                effects.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(name, Modifier.weight(1f))
                        IconButton(onClick = {
                            effects = effects - name
                            prefs.setVkBasaltEffects(effects)
                            writeConfig()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove $name")
                        }
                    }
                }
            }
        }

        LsfgCard {
            SectionHeader(eyebrow = "CONFIG", title = null)
            Text("Config: ${File(context.filesDir, "vkbasalt/vkBasalt.conf").absolutePath}")
            Text("FX directory: ${fxDir ?: "not imported"}")
            Text(
                "The old custom contrast/saturation post-process path is not used as the vkBasalt implementation; vkBasalt owns the effect chain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { writeConfig() }) { Text("Apply vkBasalt settings") }
        }
    }
}
