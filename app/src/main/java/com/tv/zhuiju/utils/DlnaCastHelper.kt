package com.tv.zhuiju.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 简单的 DLNA 投屏助手
 */
object DlnaCastHelper {

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val TAG = "DlnaCast"

    data class DlnaDevice(
        val name: String,
        val location: String,
        val controlUrl: String? = null
    )

    /**
     * 发现局域网内的 DLNA 设备
     */
    suspend fun discoverDevices(timeoutMs: Int = 5000): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DlnaDevice>()
        val discoveredLocations = mutableSetOf<String>()

        try {
            val ssdpRequest = buildString {
                appendLine("M-SEARCH * HTTP/1.1")
                appendLine("HOST: $SSDP_ADDRESS:$SSDP_PORT")
                appendLine("MAN: \"ssdp:discover\"")
                appendLine("MX: 3")
                appendLine("ST: urn:schemas-upnp-org:service:AVTransport:1")
                appendLine()
            }

            val socket = MulticastSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(SSDP_PORT))
            socket.timeToLive = 255
            socket.soTimeout = timeoutMs

            val packet = DatagramPacket(
                ssdpRequest.toByteArray(),
                ssdpRequest.toByteArray().size,
                InetAddress.getByName(SSDP_ADDRESS),
                SSDP_PORT
            )
            socket.send(packet)

            val buffer = ByteArray(2048)
            val endTime = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < endTime) {
                try {
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(recvPacket)
                    val response = String(recvPacket.data, 0, recvPacket.length)
                    val location = parseHeader(response, "LOCATION")
                        ?: parseHeader(response, "Location")

                    if (location != null && !discoveredLocations.contains(location)) {
                        discoveredLocations.add(location)
                        val deviceName = fetchDeviceName(location)
                        val controlUrl = fetchAvTransportControlUrl(location)
                        if (!deviceName.isNullOrBlank()) {
                            devices.add(DlnaDevice(deviceName, location, controlUrl))
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "DLNA discovery error", e)
        }

        devices
    }

    /**
     * 发送视频 URL 到 DLNA 设备播放
     */
    suspend fun castToDevice(device: DlnaDevice, videoUrl: String, title: String = ""): Boolean = withContext(Dispatchers.IO) {
        val controlUrl = device.controlUrl ?: return@withContext false
        try {
            // SetAVTransportURI
            val soapBody = """<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
  <s:Body>
    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <CurrentURI>${videoUrl.replace("&", "&amp;")}</CurrentURI>
      <CurrentURIMetaData>&lt;DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"&gt;&lt;item id="0" parentID="-1" restricted="1"&gt;&lt;dc:title&gt;${title}&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
    </u:SetAVTransportURI>
  </s:Body>
</s:Envelope>""".trimIndent()

            val url = URL(controlUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(soapBody)
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                // Play
                play(controlUrl)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cast error", e)
            false
        }
    }

    private fun play(controlUrl: String) {
        try {
            val soapBody = """<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
  <s:Body>
    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <Speed>1</Speed>
    </u:Play>
  </s:Body>
</s:Envelope>""".trimIndent()

            val url = URL(controlUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(soapBody)
            }
            connection.responseCode
        } catch (_: Exception) {
        }
    }

    private fun parseHeader(response: String, headerName: String): String? {
        val lines = response.lines()
        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2 && parts[0].trim().equals(headerName, ignoreCase = true)) {
                return parts[1].trim()
            }
        }
        return null
    }

    private fun fetchDeviceName(location: String): String? {
        return try {
            val url = URL(location)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val xml = reader.readText()
            reader.close()

            // Simple XML parsing for friendlyName
            val regex = "<friendlyName>([^<]+)</friendlyName>".toRegex()
            regex.find(xml)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchAvTransportControlUrl(location: String): String? {
        return try {
            val url = URL(location)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val xml = reader.readText()
            reader.close()

            // Find AVTransport service controlURL
            val serviceRegex = "<service>.*?<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>.*?<controlURL>([^<]+)</controlURL>.*?</service>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = serviceRegex.find(xml)
            if (match != null) {
                val controlPath = match.groupValues[1]
                val baseUrl = location.substringBeforeLast("/").substringBeforeLast("/")
                if (controlPath.startsWith("/")) {
                    "$baseUrl$controlPath"
                } else {
                    "$baseUrl/$controlPath"
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
