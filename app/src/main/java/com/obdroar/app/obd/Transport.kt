package com.obdroar.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** A byte pipe to an ELM327-compatible adapter. */
interface ObdTransport {
    val displayName: String
    suspend fun open()

    /** Sends [command] + CR and returns everything received up to the '>' prompt. */
    suspend fun exchange(command: String, timeoutMs: Long = 4000): String
    fun close()
}

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/** Classic Bluetooth (SPP / RFCOMM) transport - what >95% of cheap ELM327 dongles use. */
@SuppressLint("MissingPermission")
class BluetoothTransport(private val device: BluetoothDevice) : ObdTransport {

    override val displayName: String =
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun open() {
        withContext(Dispatchers.IO) {
            val factories = listOf<() -> BluetoothSocket>(
                { device.createRfcommSocketToServiceRecord(SPP_UUID) },
                { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
                // Fallback that many Chinese clones need: raw RFCOMM channel 1.
                {
                    device.javaClass
                        .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                }
            )
            var lastError: Exception? = null
            for (factory in factories) {
                var s: BluetoothSocket? = null
                try {
                    s = factory()
                    s.connect()
                    socket = s
                    input = s.inputStream
                    output = s.outputStream
                    return@withContext
                } catch (e: Exception) {
                    lastError = e
                    runCatching { s?.close() }
                }
            }
            throw IOException("Could not connect to $displayName (${lastError?.message ?: "unknown error"})")
        }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String =
        withContext(Dispatchers.IO) {
            val inp = input ?: throw IOException("Not connected")
            val out = output ?: throw IOException("Not connected")
            while (inp.available() > 0) inp.read() // drop stale bytes
            out.write((command + "\r").toByteArray(Charsets.US_ASCII))
            out.flush()

            val sb = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (inp.available() > 0) {
                    val c = inp.read()
                    if (c < 0) throw IOException("Connection closed by adapter")
                    val ch = c.toChar()
                    if (ch == '>') return@withContext sb.toString()
                    sb.append(ch)
                } else {
                    delay(4)
                }
            }
            throw IOException("Timeout waiting for reply to $command")
        }

    override fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }
}
