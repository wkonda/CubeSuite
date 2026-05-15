package com.wkonda.cubesuite.mvave

data class Settings(
    val general: Module.General, val cab: Module.CAB, val mod: Module.MOD, val amp: Module.AMP
) {
    companion object {
        private fun ByteArray.v(s: Setting) = this[s.code.toInt()]

        fun parseFromValues(b: ByteArray) = Settings(
            general = Module.General(
                b.v(Setting.VOLUME), b.v(Setting.AMP_SW), b.v(Setting.CAB_SW), b.v(Setting.MOD_SW)
            ), cab = Module.CAB(
                b.v(Setting.IR), b.v(Setting.REVERB)
            ), mod = Module.MOD(
                b.v(Setting.MIX), b.v(Setting.FB), b.v(Setting.TIME), b.v(Setting.MOD)
            ), amp = Module.AMP(
                b.v(Setting.TONE), b.v(Setting.GAIN), b.v(Setting.TYPE)
            )
        )
    }

    fun toBytes() = ByteArray(16).apply {
        Setting.entries.forEach { s ->
            this[s.code.toInt()] = when (s) {
                Setting.TYPE -> amp.type
                Setting.GAIN -> amp.gain
                Setting.TONE -> amp.tone
                Setting.REVERB -> cab.reverb
                Setting.FB -> mod.fb
                Setting.VOLUME -> general.volume
                Setting.TIME -> mod.time
                Setting.MIX -> mod.mix
                Setting.MOD -> mod.mod
                Setting.IR -> cab.ir
                Setting.CAB_SW -> general.cabSw
                Setting.MOD_SW -> general.modSw
                Setting.AMP_SW -> general.ampSw
            }
        }
    }

    fun update(setting: Setting, value: Byte): Settings {
        return when (setting) {
            Setting.VOLUME -> copy(general = general.copy(volume = value))
            Setting.AMP_SW -> copy(general = general.copy(ampSw = value))
            Setting.CAB_SW -> copy(general = general.copy(cabSw = value))
            Setting.MOD_SW -> copy(general = general.copy(modSw = value))
            Setting.IR -> copy(cab = cab.copy(ir = value))
            Setting.REVERB -> copy(cab = cab.copy(reverb = value))
            Setting.MIX -> copy(mod = mod.copy(mix = value))
            Setting.FB -> copy(mod = mod.copy(fb = value))
            Setting.TIME -> copy(mod = mod.copy(time = value))
            Setting.MOD -> copy(mod = mod.copy(mod = value))
            Setting.TONE -> copy(amp = amp.copy(tone = value))
            Setting.GAIN -> copy(amp = amp.copy(gain = value))
            Setting.TYPE -> copy(amp = amp.copy(type = value))
        }
    }
}