package com.weefic.xtun.vmess

object VMessConsts {
    val KDFSaltConstVMessAEADKDF = "VMess AEAD KDF".encodeToByteArray()
    val KDFSaltConstAuthIDEncryptionKey = "AES Auth ID Encryption".encodeToByteArray()
    val KDFSaltConstVMessHeaderPayloadLengthAEADKey = "VMess Header AEAD Key_Length".encodeToByteArray()
    val KDFSaltConstVMessHeaderPayloadLengthAEADIV = "VMess Header AEAD Nonce_Length".encodeToByteArray()
    val KDFSaltConstVMessHeaderPayloadAEADKey = "VMess Header AEAD Key".encodeToByteArray()
    val KDFSaltConstVMessHeaderPayloadAEADIV = "VMess Header AEAD Nonce".encodeToByteArray()
}