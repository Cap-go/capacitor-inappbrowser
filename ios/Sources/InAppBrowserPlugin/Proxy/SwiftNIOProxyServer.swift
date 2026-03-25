import Foundation
import NIO
import NIOCore
import NIOPosix
import NIOHTTP1

/// Delegate protocol for proxy interception events.
///
/// The delegate (typically the plugin class) fires Capacitor events to
/// JavaScript and waits for the JS side to call back with optional
/// modifications before resuming the proxied request/response.
protocol ProxyEventDelegate: AnyObject {
    /// Called when a request matches an interception rule.
    /// `completion` receives optional modifications, or `nil` to forward as-is.
    func onRequestIntercept(requestId: String, ruleIndex: Int,
                            requestData: [String: Any],
                            completion: @escaping ([String: Any]?) -> Void)

    /// Called when a response matches an interception rule.
    /// `completion` receives optional modifications, or `nil` to forward as-is.
    func onResponseIntercept(requestId: String, ruleIndex: Int,
                             responseData: [String: Any],
                             completion: @escaping ([String: Any]?) -> Void)
}

/// A local HTTP proxy server built on SwiftNIO.
///
/// Behaviour:
///  - Binds to `127.0.0.1` on a random port.
///  - Regular HTTP requests: parsed, rule-matched, events fired.
///  - CONNECT requests: blind TCP tunnel (MITM requires full cert generation,
///    which is a TODO — see `CertificateAuthority`).
class SwiftNIOProxyServer {

    private var channel: Channel?
    private let group: MultiThreadedEventLoopGroup
    private let ruleMatcher: ProxyRuleMatcher
    weak var delegate: ProxyEventDelegate?
    private(set) var isProxyActive: Bool = false

    static let timeoutSeconds: Int64 = 10

    init(ruleMatcher: ProxyRuleMatcher) {
        self.ruleMatcher = ruleMatcher
        self.group = MultiThreadedEventLoopGroup(numberOfThreads: 2)
    }

    /// Starts the proxy and returns the port it is listening on.
    func start() throws -> Int {
        let bootstrap = ServerBootstrap(group: group)
            .serverChannelOption(.backlog, value: 256)
            .childChannelInitializer { [weak self] channel in
                guard let self = self else {
                    return channel.eventLoop.makeFailedFuture(ProxyError.serverStopped)
                }
                return channel.pipeline.configureHTTPServerPipeline().flatMap {
                    channel.pipeline.addHandler(
                        ProxyHTTPHandler(
                            ruleMatcher: self.ruleMatcher,
                            delegate: self.delegate
                        )
                    )
                }
            }
            .childChannelOption(.socketOption(.so_reuseaddr), value: 1)

        channel = try bootstrap.bind(host: "127.0.0.1", port: 0).wait()
        isProxyActive = true

        guard let port = channel?.localAddress?.port else {
            throw ProxyError.bindFailed
        }
        print("[SwiftNIOProxyServer] Listening on 127.0.0.1:\(port)")
        return port
    }

    /// Stops the proxy server and releases resources.
    func stop() {
        isProxyActive = false
        try? channel?.close().wait()
        try? group.syncShutdownGracefully()
        channel = nil
        print("[SwiftNIOProxyServer] Stopped")
    }
}
