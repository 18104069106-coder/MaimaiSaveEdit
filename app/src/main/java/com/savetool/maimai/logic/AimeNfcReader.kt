package com.savetool.maimai.logic

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.nfc.tech.NfcF
import java.io.IOException

object AimeNfcReader {

    fun readAccessCode(tag: Tag): String? {
        val techList = tag.techList ?: return null

        return when {
            techList.contains(NfcF::class.java.name) -> {
                handleAmusementIC(tag)
            }
            techList.contains(MifareClassic::class.java.name) || techList.contains(NfcA::class.java.name) -> {
                handleLegacyCard(tag)
            }
            else -> null
        }
    }

    private fun handleAmusementIC(tag: Tag): String? {
        val nfcf = NfcF.get(tag) ?: return null
        try {
            nfcf.connect()
            val idm = tag.id
            
            // Construct 'Read Without Encryption' Command to read Blocks 0, 1, 2, 3
            // Length(1) + Cmd(0x06)(1) + IDm(8) + SvcCount(1) + SvcCode(2) + BlkCount(1) + BlkList(8) = 22 bytes
            val command = ByteArray(22)
            command[0] = 22.toByte()                  // Packet Length
            command[1] = 0x06.toByte()                // Command Code: Read
            System.arraycopy(idm, 0, command, 2, 8)   // IDm (8 bytes)
            
            command[10] = 1.toByte()                  // Number of Services (1)
            command[11] = 0x0B.toByte()               // Service Code Lower Byte (0x0B)
            command[12] = 0x00.toByte()               // Service Code Upper Byte (0x00)
            
            command[13] = 4.toByte()                  // Number of Blocks to read (4 blocks)
            // Block 0
            command[14] = 0x80.toByte()
            command[15] = 0x00.toByte()
            // Block 1
            command[16] = 0x80.toByte()
            command[17] = 0x01.toByte()
            // Block 2
            command[18] = 0x80.toByte()
            command[19] = 0x02.toByte()
            // Block 3
            command[20] = 0x80.toByte()
            command[21] = 0x03.toByte()

            val response = nfcf.transceive(command)
            
            // Parse Response
            // Length (1) + RespCode (1) + IDm (8) + Status1 (1) + Status2 (1) + BlockCount (1) + BlockData (16 * n)
            if (response != null && response.size >= 13 && response[1] == 0x07.toByte()) {
                val status1 = response[10]
                val status2 = response[11]
                
                if (status1 == 0x00.toByte() && status2 == 0x00.toByte()) {
                    // Extract block data (offset 13 to end)
                    val blockData = response.copyOfRange(13, response.size)
                    
                    // Attempt 1: Parse as ASCII (usually 20 characters in block 1 & 2)
                    val asciiStr = String(blockData, Charsets.US_ASCII)
                    val asciiMatch = Regex("\\d{20}").find(asciiStr)
                    if (asciiMatch != null) {
                        return asciiMatch.value
                    }
                    
                    // Attempt 2: Parse as Hex/BCD (if it's 10 bytes BCD format)
                    val hexStr = blockData.joinToString("") { String.format(java.util.Locale.US, "%02X", it) }
                    val hexMatch = Regex("\\d{20}").find(hexStr)
                    if (hexMatch != null) {
                        return hexMatch.value
                    }
                }
            }
            
            return null
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            try {
                nfcf.close()
            } catch (e: Exception) {}
        }
    }

    private fun handleLegacyCard(tag: Tag): String? {
        // Simple Legacy Banapassport calculation
        // UID to decimal, padded to 20 digits
        val uid = tag.id
        if (uid.size >= 4) {
            var decimalValue = 0L
            for (i in 0 until 4) {
                decimalValue = (decimalValue shl 8) or (uid[i].toLong() and 0xFFL)
            }
            // For now just implementing Banapassport simple calculation, 
            // the exact Aime math requires the "secret key" which we don't have.
            return String.format(java.util.Locale.US, "%020d", decimalValue)
        }
        return null
    }
}
