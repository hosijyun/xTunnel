package com.weefic.xtun.shadowsocks

import com.weefic.xtun.ShadowsocksEncryptionMethod
import com.weefic.xtun.shadowsocks.cipher.*
import io.netty.channel.ChannelPipeline

fun ShadowsocksEncryptionMethod.config(pipeline: ChannelPipeline, password: String) {
    when (this) {
        ShadowsocksEncryptionMethod.None -> {
        }
        ShadowsocksEncryptionMethod.AES128GCM -> {
            pipeline.addLast(ShadowSocksInboundAEADDecoder(password, AES128GCMCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundAEADEncoder(password, AES128GCMCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES192GCM -> {
            pipeline.addLast(ShadowSocksInboundAEADDecoder(password, AES192GCMCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundAEADEncoder(password, AES192GCMCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES256GCM -> {
            pipeline.addLast(ShadowSocksInboundAEADDecoder(password, AES256GCMCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundAEADEncoder(password, AES256GCMCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES128CFB -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, AES128CFBCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, AES128CFBCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES192CFB -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, AES192CFBCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, AES192CFBCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES256CFB -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, AES256CFBCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, AES256CFBCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES128CTR -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, AES128CTRCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, AES128CTRCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES192CTR -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, AES192CTRCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, AES192CTRCipherProvider()))
        }
        ShadowsocksEncryptionMethod.AES256CTR -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, AES256CTRCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, AES256CTRCipherProvider()))
        }
        ShadowsocksEncryptionMethod.Camellia128CFB -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, Camellia128CFBCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, Camellia128CFBCipherProvider()))
        }
        ShadowsocksEncryptionMethod.Camellia192CFB -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, Camellia192CFBCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, Camellia192CFBCipherProvider()))
        }
        ShadowsocksEncryptionMethod.Camellia256CFB -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, Camellia256CFBCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, Camellia256CFBCipherProvider()))
        }
        ShadowsocksEncryptionMethod.Chacha20IETFPoly1305 -> {
            pipeline.addLast(ShadowSocksInboundAEADDecoder(password, Chacha20IETFPoly1305CipherProvider()))
            pipeline.addLast(ShadowSocksOutboundAEADEncoder(password, Chacha20IETFPoly1305CipherProvider()))
        }
        ShadowsocksEncryptionMethod.Salsa20 -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, Salsa20CipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, Salsa20CipherProvider()))
        }
        ShadowsocksEncryptionMethod.Chacha20 -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, Chacha20CipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, Chacha20CipherProvider()))
        }
        ShadowsocksEncryptionMethod.Chacha20IETF -> {
            pipeline.addLast(ShadowSocksInboundDecoder(password, Chacha20IETFCipherProvider()))
            pipeline.addLast(ShadowSocksOutboundEncoder(password, Chacha20IETFCipherProvider()))
        }
    }
}