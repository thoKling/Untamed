package dev.untamed.launcher.infrastructure.account

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.untamed.launcher.application.installation.CancellationSignal
import dev.untamed.launcher.branding.Branding
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The socket the browser comes back to.
 *
 * A loopback redirect is what Microsoft recommends for a native application,
 * and it is why this launcher never renders a login form: the user types their
 * password into Microsoft's own page, and all that arrives here is a
 * single-use authorization code that is worth nothing without the PKCE
 * verifier that never left the process.
 *
 * Bound to the loopback address and to a port the operating system chooses, so
 * nothing off this machine can reach it, and two launchers signing in at once
 * do not fight over a fixed port. It exists for the length of one sign-in.
 *
 * This is the one plain-HTTP address in the launcher, and it is not a hole in
 * the HTTPS rule: the traffic is a browser on this machine talking to this
 * process, and it never reaches a network. Every request that leaves the
 * machine still goes through the launcher's one HTTP transport, which refuses
 * anything that is not HTTPS.
 */
internal class LoopbackRedirect(private val log: LauncherLog) : Closeable {

    private val responses = ArrayBlockingQueue<String>(1)
    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), ANY_PORT), BACKLOG)

    /** Where Microsoft is told to send the browser. Must match the registration. */
    val redirectUri: String get() = "http://localhost:${server.address.port}/"

    init {
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    /**
     * Waits for the browser to arrive and returns the request target, or null
     * if the user cancelled or took too long.
     *
     * Polled rather than blocked outright so that a Cancel button and a
     * timeout both work. A sign-in window left open forever is a socket left
     * open forever.
     */
    fun awaitRedirect(timeout: Duration, cancellation: CancellationSignal): String? {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (cancellation.isCancelled()) return null
            val target = responses.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)
            if (target != null) return target
        }
        return null
    }

    override fun close() {
        // No delay: the page has already been written by the time anything
        // calls this, and a sign-in should not hold the port open afterwards.
        runCatching { server.stop(0) }
            .onFailure { log.warn(CATEGORY, "The sign-in listener did not stop cleanly") }
    }

    /**
     * Answers the browser and hands the request on.
     *
     * A request with no query is not the redirect. Browsers ask for a favicon
     * on their own, and answering one of those as though it were the sign-in
     * would end the wait with nothing in it.
     */
    private fun handle(exchange: HttpExchange) {
        val query = exchange.requestURI.rawQuery
        if (query.isNullOrBlank()) {
            respond(exchange, NOT_FOUND, "Not found")
            return
        }
        val target = "${exchange.requestURI.rawPath}?$query"
        respond(exchange, OK, PAGE)
        // Offered rather than put: the queue holds one, and a second request
        // arriving after the first is not a second sign-in.
        responses.offer(target)
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        runCatching {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }.onFailure {
            log.warn(CATEGORY, "Could not answer the browser after sign-in")
        }
    }

    private companion object {
        const val CATEGORY = "account"
        const val ANY_PORT = 0
        const val BACKLOG = 1
        const val POLL_MILLIS = 150L
        const val OK = 200
        const val NOT_FOUND = 404

        /**
         * What the browser shows when the redirect lands. Deliberately plain:
         * no script, no external stylesheet, no image, nothing that reaches the
         * network from a page served by a launcher.
         */
        val PAGE = """
            <!doctype html>
            <html lang="en">
            <head><meta charset="utf-8"><title>${Branding.PRODUCT_NAME}</title></head>
            <body style="font-family: system-ui, sans-serif; background: #14100f; color: #f2ece6;
                         display: flex; align-items: center; justify-content: center;
                         height: 100vh; margin: 0;">
              <div style="text-align: center; max-width: 30rem;">
                <h1 style="font-size: 1.4rem;">You are signed in</h1>
                <p style="color: #b9aea4;">You can close this tab and go back to the
                ${Branding.PRODUCT_NAME} launcher.</p>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
