package com.wkonda.cubesuite.mvave

data class Settings(
    val general: Module.General, val cab: Module.CAB, val mod: Module.MOD, val amp: Module.AMP
) {
    companion object {
        fun parseFromValues(b: ByteArray) = Settings(
            general = Module.General(b[5], b[12], b[10], b[11]),
            cab = Module.CAB(b[9], b[3]),
            mod = Module.MOD(b[7], b[4], b[6], b[8]),
            amp = Module.AMP(b[2], b[1], b[0])
        )
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