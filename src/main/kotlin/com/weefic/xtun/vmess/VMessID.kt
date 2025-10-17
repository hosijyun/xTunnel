package com.weefic.xtun.vmess

import com.weefic.xtun.utils.toByteArray
import com.weefic.xtun.utils.toUUID
import java.security.MessageDigest
import java.util.*

class VMessID(val uuid: UUID) {
    companion object {
        fun getCmdKey(u: UUID): ByteArray {
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(u.toByteArray())
            md5.update("c48619fe-8f02-49e0-b9e9-edf763e17e21".encodeToByteArray())
            return md5.digest()
        }

        fun nextID(u: VMessID): VMessID {
            val md5 = MessageDigest.getInstance("MD5")
            val uData = u.uuid.toByteArray()
            md5.update(uData)
            md5.update("16167dc8-16b6-4e6d-b8bb-65dd68113a81".encodeToByteArray())
            while (true) {
                val newId = md5.digest()
                if (!newId.contentEquals(uData)) {
                    return VMessID(newId.toUUID())
                }
                md5.update("533eff8a-4113-4b10-b5ce-0f5d76b98cd2".encodeToByteArray())
            }
        }
    }

    val cmdKey: ByteArray = VMessID.getCmdKey(this.uuid)
}