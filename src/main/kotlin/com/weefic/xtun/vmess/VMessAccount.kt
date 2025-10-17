package com.weefic.xtun.vmess

import java.util.*

class VMessAccount {
    val id: VMessID
    val alterIDs: List<VMessID>
    val security: VMessSecurityType
    val aead: Boolean

    constructor(id: UUID, alterID: Int, security: VMessSecurityType = VMessSecurityType.AES128GCM) {
        check(alterID >= 0 && alterID <= 65535) { throw IllegalArgumentException("alterID must be between 0 and 65535") }
        this.id = VMessID(id)
        var prevId = this.id
        this.alterIDs = List(alterID) {
            VMessID.nextID(prevId).also {
                prevId = it
            }
        }
        this.security = security
        this.aead = alterID == 0
    }
}
