import platform.posix.nanosleep
import platform.posix.timespec
import kotlinx.cinterop.*
import tox.*

enum class ToxLogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

@OptIn(ExperimentalForeignApi::class)
fun fromNative(level: Tox_Log_Level): ToxLogLevel = when(level) {
    Tox_Log_Level.TOX_LOG_LEVEL_TRACE -> ToxLogLevel.TRACE
    Tox_Log_Level.TOX_LOG_LEVEL_DEBUG -> ToxLogLevel.DEBUG
    Tox_Log_Level.TOX_LOG_LEVEL_INFO -> ToxLogLevel.INFO
    Tox_Log_Level.TOX_LOG_LEVEL_WARNING -> ToxLogLevel.WARNING
    Tox_Log_Level.TOX_LOG_LEVEL_ERROR -> ToxLogLevel.ERROR
}

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
class ToxOptions : AutoCloseable {
    internal val ptr: CPointer<tox.Tox_Options>
    var logCallback: ((ToxLogLevel, String, UInt, String, String) -> Unit) = { _, _, _, _, _ -> }

    init {
        ptr = memScoped {
            val err = alloc<Tox_Err_Options_New.Var>();
            val res = tox_options_new(err.ptr)
            if (err.value != Tox_Err_Options_New.TOX_ERR_OPTIONS_NEW_OK) {
                throw Exception(err.value.toString())
            }
            res ?: throw Exception("null result but error is OK")
        }

        tox_options_set_log_user_data(this.ptr, StableRef.create(this).asCPointer())
        tox_options_set_log_callback(this.ptr, staticCFunction<CPointer<tox.Tox>?, Tox_Log_Level, CPointer<ByteVar>?, UInt, CPointer<ByteVar>?, CPointer<ByteVar>?, COpaquePointer?, Unit> { _, level, file, line, func, message, userData ->
            val self = userData!!.asStableRef<ToxOptions>().get()
            self.logCallback(fromNative(level), file?.toKString() ?: "", line, func?.toKString() ?: "", message?.toKString() ?: "")
        })
    }

    override fun close(): Unit {
        tox_options_free(ptr)
    }

    var ipv6Enabled: Boolean
        get() = tox_options_get_ipv6_enabled(ptr)
        set(value) = tox_options_set_ipv6_enabled(ptr, value)
    var udpEnabled: Boolean
        get() = tox_options_get_udp_enabled(ptr)
        set(value) = tox_options_set_udp_enabled(ptr, value)
    var localDiscoveryEnabled: Boolean
        get() = tox_options_get_local_discovery_enabled(ptr)
        set(value) = tox_options_set_local_discovery_enabled(ptr, value)
    var dhtAnnouncementsEnabled: Boolean
        get() = tox_options_get_dht_announcements_enabled(ptr)
        set(value) = tox_options_set_dht_announcements_enabled(ptr, value)
}

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
class Tox(opts: ToxOptions) : AutoCloseable {
    internal val ptr: CPointer<tox.Tox>

    init {
        ptr = memScoped {
            val err = alloc<Tox_Err_New.Var>();
            val res = tox_new(opts.ptr, err.ptr)
            if (err.value != Tox_Err_New.TOX_ERR_NEW_OK) {
                throw Exception(err.value.toString())
            }
            res ?: throw Exception("null result but error is OK")
        }
    }

    override fun close(): Unit {
        tox_kill(ptr)
    }

    fun bootstrap(host: String, port: UShort, publicKey: Array<UByte>): Unit {
        memScoped {
            val err = alloc<Tox_Err_Bootstrap.Var>();
            tox_bootstrap(ptr, host, port, publicKey.toUByteArray().toCValues(), err.ptr)
            if (err.value != Tox_Err_Bootstrap.TOX_ERR_BOOTSTRAP_OK) {
                throw Exception(err.value.toString())
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
object Main {
    fun sleep(ms: Long) {
        val time = cValue<timespec> {
            tv_sec = ms / 1000
            tv_nsec = (ms % 1000) * 1000 * 1000
        }

        nanosleep(time, null)
    }

    fun main(args: Array<String>) {
        ToxOptions().use { opts ->
            opts.logCallback = { level, file, line, func, message ->
                if (level != ToxLogLevel.TRACE) {
                    println("[$level] $file:$line($func): $message")
                }
            }
            Tox(opts)
        }.use { tox ->
            println(tox)

            tox.bootstrap("tox.initramfs.io", 33445u, arrayOf<UByte>(
                0x02u, 0x80u, 0x7Cu, 0xF4u, 0xF8u, 0xBBu, 0x8Fu, 0xB3u,
                0x90u, 0xCCu, 0x37u, 0x94u, 0xBDu, 0xF1u, 0xE8u, 0x44u,
                0x9Eu, 0x9Au, 0x83u, 0x92u, 0xC5u, 0xD3u, 0xF2u, 0x20u,
                0x00u, 0x19u, 0xDAu, 0x9Fu, 0x1Eu, 0x81u, 0x2Eu, 0x46u,
            ))

            tox.bootstrap("tox.abilinski.com", 33445u, arrayOf<UByte>(
                0x10u, 0xC0u, 0x0Eu, 0xB2u, 0x50u, 0xC3u, 0x23u, 0x3Eu,
                0x34u, 0x3Eu, 0x2Au, 0xEBu, 0xA0u, 0x71u, 0x15u, 0xA5u,
                0xC2u, 0x89u, 0x20u, 0xE9u, 0xC8u, 0xD2u, 0x94u, 0x92u,
                0xF6u, 0xD0u, 0x0Bu, 0x29u, 0x04u, 0x9Eu, 0xDCu, 0x7Eu,
            ))

            while (tox_self_get_connection_status(tox.ptr) == Tox_Connection.TOX_CONNECTION_NONE) {
                println("iterate: " + tox_self_get_connection_status(tox.ptr))
                tox_iterate(tox.ptr, null)
                sleep(tox_iteration_interval(tox.ptr).toLong())
            }
        }
    }
}

fun main(args: Array<String>) {
    Main.main(args)
}
