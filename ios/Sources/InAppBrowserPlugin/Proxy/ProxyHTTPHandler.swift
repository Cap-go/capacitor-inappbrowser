// swiftlint:disable file_length
#if canImport(NIO) && canImport(NIOCore) && canImport(NIOPosix) && canImport(NIOHTTP1) && canImport(NIOSSL) && canImport(X509) && canImport(SwiftASN1) && canImport(Crypto) && canImport(_CryptoExtras)
import Foundation
import NIO
import NIOCore
import NIOHTTP1
import NIOSSL

private struct ResolvedUpstreamRequest {
    let upstreamURL: URL
    let requestHead: HTTPRequestHead
}

private struct UpstreamForwardContext {
    let clientContext: ChannelHandlerContext
    let delegate: ProxyEventDelegate?
    let responseRule: NativeProxyRule?
    let requestId: String?
    let requestUrl: String
    let requestHead: HTTPRequestHead
    let body: ByteBuffer?
}

private func proxyHeadersPayload(from headers: HTTPHeaders) -> [String: Any] {
    var grouped: [String: [String]] = [:]
    for header in headers {
        grouped[header.name, default: []].append(header.value)
    }

    return grouped.reduce(into: [:]) { result, entry in
        if entry.value.count == 1, let value = entry.value.first {
            result[entry.key] = value
        } else {
            result[entry.key] = entry.value
        }
    }
}

private func httpHeaders(from payload: [String: Any]) -> HTTPHeaders {
    var headers = HTTPHeaders()
    for (name, value) in payload {
        for headerValue in proxyHeaderValues(from: value) {
            headers.add(name: name, value: headerValue)
        }
    }
    return headers
}

private func proxyHeaderValues(from value: Any) -> [String] {
    if let headerValue = value as? String {
        return [headerValue]
    }
    if let headerValues = value as? [String] {
        return headerValues
    }
    if let headerValues = value as? [Any] {
        return headerValues.map { String(describing: $0) }
    }
    return [String(describing: value)]
}

private func defaultPort(for scheme: String) -> Int? {
    switch scheme.lowercased() {
    case "http":
        return 80
    case "https":
        return 443
    default:
        return nil
    }
}

private func hostHeaderValue(for url: URL) -> String? {
    guard let host = url.host else {
        return nil
    }

    if let port = url.port, port != defaultPort(for: url.scheme ?? "") {
        return "\(host):\(port)"
    }
    return host
}

private func originForm(for url: URL) -> String {
    let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
    var requestURI = components?.percentEncodedPath ?? url.path
    if requestURI.isEmpty {
        requestURI = "/"
    }
    if let query = components?.percentEncodedQuery, !query.isEmpty {
        requestURI += "?\(query)"
    }
    return requestURI
}

private func resolveUpstreamRequest(from head: HTTPRequestHead,
                                    defaultScheme: String? = nil,
                                    defaultHost: String? = nil,
                                    defaultPortValue: Int? = nil) -> ResolvedUpstreamRequest? {
    let upstreamURL: URL

    if let absoluteURL = URL(string: head.uri), absoluteURL.scheme != nil, absoluteURL.host != nil {
        upstreamURL = absoluteURL
    } else {
        guard let defaultHost, let scheme = defaultScheme else {
            return nil
        }

        var components = URLComponents()
        components.scheme = scheme
        components.host = defaultHost
        if let defaultPortValue, defaultPortValue != defaultPort(for: scheme) {
            components.port = defaultPortValue
        }
        guard let baseURL = components.url else {
            return nil
        }

        let relativeURI = head.uri.hasPrefix("/") ? head.uri : "/\(head.uri)"
        guard let relativeURL = URL(string: relativeURI, relativeTo: baseURL)?.absoluteURL else {
            return nil
        }
        upstreamURL = relativeURL
    }

    var requestHead = head
    requestHead.uri = originForm(for: upstreamURL)
    if let hostHeader = hostHeaderValue(for: upstreamURL) {
        requestHead.headers.replaceOrAdd(name: "host", value: hostHeader)
    }

    return ResolvedUpstreamRequest(upstreamURL: upstreamURL, requestHead: requestHead)
}

private func sendUpstreamRequest(
    on upstreamChannel: Channel,
    requestHead: HTTPRequestHead,
    body: ByteBuffer?
) -> EventLoopFuture<Void> {
    let promise = upstreamChannel.eventLoop.makePromise(of: Void.self)
    upstreamChannel.write(NIOAny(HTTPClientRequestPart.head(requestHead)), promise: nil)
    if let body, body.readableBytes > 0 {
        upstreamChannel.write(NIOAny(HTTPClientRequestPart.body(.byteBuffer(body))), promise: nil)
    }
    upstreamChannel.writeAndFlush(NIOAny(HTTPClientRequestPart.end(nil)), promise: promise)
    return promise.futureResult
}

private func handleUpstreamConnection(
    _ result: Result<Channel, Error>,
    forward: UpstreamForwardContext,
    onFailure: @escaping () -> Void
) {
    switch result {
    case .success(let upstreamChannel):
        let responseHandler = UpstreamResponseHandler(
            clientContext: forward.clientContext,
            rule: forward.responseRule,
            delegate: forward.delegate,
            requestUrl: forward.requestUrl,
            requestId: forward.requestId
        )

        upstreamChannel.pipeline.addHandler(responseHandler)
            .flatMap {
                sendUpstreamRequest(on: upstreamChannel, requestHead: forward.requestHead, body: forward.body)
            }
            .whenFailure { _ in
                forward.clientContext.close(promise: nil)
            }

    case .failure:
        onFailure()
    }
}

private func parseConnectTarget(_ authority: String) -> (host: String, port: Int) {
    if authority.hasPrefix("["),
       let closingBracket = authority.firstIndex(of: "]") {
        let hostStart = authority.index(after: authority.startIndex)
        let host = String(authority[hostStart..<closingBracket])
        let remainderStart = authority.index(after: closingBracket)
        if remainderStart < authority.endIndex,
           authority[remainderStart] == ":" {
            let portStart = authority.index(after: remainderStart)
            let port = Int(authority[portStart...]) ?? 443
            return (host, port)
        }
        return (host, 443)
    }

    if let separator = authority.lastIndex(of: ":"), separator != authority.startIndex {
        let host = String(authority[..<separator])
        let portStart = authority.index(after: separator)
        let port = Int(authority[portStart...]) ?? 443
        return (host, port)
    }

    return (authority, 443)
}

private func buildRequestEventData(
    requestUrl: String,
    method: String,
    headers: HTTPHeaders,
    body: ByteBuffer?,
    includeBody: Bool
) -> [String: Any] {
    var requestData: [String: Any] = [
        "url": requestUrl,
        "method": method,
        "headers": proxyHeadersPayload(from: headers),
    ]

    if includeBody, let body, body.readableBytes > 0 {
        requestData["body"] = Data(body.readableBytesView).base64EncodedString()
    }

    return requestData
}

private func applyRequestModifications(
    _ modifications: [String: Any]?,
    to head: inout HTTPRequestHead,
    body: inout ByteBuffer?,
    allocator: ByteBufferAllocator
) {
    guard let modifications else {
        return
    }

    if let newUrl = modifications["url"] as? String {
        head.uri = newUrl
    }
    if let newMethod = modifications["method"] as? String {
        head.method = HTTPMethod(rawValue: newMethod)
    }
    if let newHeaders = modifications["headers"] as? [String: Any] {
        head.headers = httpHeaders(from: newHeaders)
    }
    if let newBody = modifications["body"] as? String,
       let bodyData = Data(base64Encoded: newBody) {
        body = allocator.buffer(bytes: bodyData)
        head.headers.replaceOrAdd(name: "content-length", value: "\(bodyData.count)")
    }
}

private func buildResponseEventData(
    requestUrl: String,
    head: HTTPResponseHead,
    body: ByteBuffer?,
    includeBody: Bool
) -> [String: Any] {
    var responseData: [String: Any] = [
        "url": requestUrl,
        "status": Int(head.status.code),
        "headers": proxyHeadersPayload(from: head.headers),
    ]

    if includeBody, let body, body.readableBytes > 0 {
        responseData["body"] = Data(body.readableBytesView).base64EncodedString()
    }

    return responseData
}

private func applyResponseModifications(
    _ modifications: [String: Any]?,
    to head: inout HTTPResponseHead,
    body: inout ByteBuffer?,
    allocator: ByteBufferAllocator
) {
    guard let modifications else {
        return
    }

    if let status = modifications["status"] as? Int {
        head.status = HTTPResponseStatus(statusCode: status)
    }
    if let newHeaders = modifications["headers"] as? [String: Any] {
        head.headers = httpHeaders(from: newHeaders)
    }
    if let newBody = modifications["body"] as? String,
       let bodyData = Data(base64Encoded: newBody) {
        body = allocator.buffer(bytes: bodyData)
        head.headers.replaceOrAdd(name: "content-length", value: "\(bodyData.count)")
    }
}

private func dispatchRequestIntercept(
    delegate: ProxyEventDelegate?,
    requestId: String,
    ruleName: String,
    requestData: [String: Any],
    eventLoop: EventLoop,
    completion: @escaping ([String: Any]?) -> Void
) {
    guard let delegate else {
        eventLoop.execute {
            completion(nil)
        }
        return
    }

    delegate.onRequestIntercept(requestId: requestId, ruleName: ruleName, requestData: requestData) { modifications in
        eventLoop.execute {
            completion(modifications)
        }
    }
}

private func dispatchResponseIntercept(
    delegate: ProxyEventDelegate?,
    requestId: String,
    ruleName: String,
    responseData: [String: Any],
    eventLoop: EventLoop,
    completion: @escaping ([String: Any]?) -> Void
) {
    guard let delegate else {
        eventLoop.execute {
            completion(nil)
        }
        return
    }

    delegate.onResponseIntercept(requestId: requestId, ruleName: ruleName, responseData: responseData) { modifications in
        eventLoop.execute {
            completion(modifications)
        }
    }
}

// MARK: - ProxyHTTPHandler

/// Handles incoming HTTP connections from the WKWebView.
///
/// For `CONNECT` requests it either performs MITM TLS termination (when a
/// ``CertificateAuthority`` is available and a rule could match the host)
/// or establishes a blind TCP tunnel.
///
/// For regular HTTP requests it matches rules, optionally fires
/// interception events, and forwards to the upstream server.
final class ProxyHTTPHandler: ChannelInboundHandler, RemovableChannelHandler {
    typealias InboundIn = HTTPServerRequestPart
    typealias OutboundOut = HTTPServerResponsePart

    private let ruleMatcher: ProxyRuleMatcher
    private weak var delegate: ProxyEventDelegate?
    private let certificateAuthority: CertificateAuthority?

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    init(ruleMatcher: ProxyRuleMatcher,
         delegate: ProxyEventDelegate?,
         certificateAuthority: CertificateAuthority?) {
        self.ruleMatcher = ruleMatcher
        self.delegate = delegate
        self.certificateAuthority = certificateAuthority
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let part = unwrapInboundIn(data)

        switch part {
        case .head(let head):
            requestHead = head
            requestBody = context.channel.allocator.buffer(capacity: 0)

            if head.method == .CONNECT {
                handleConnect(context: context, head: head)
                return
            }

        case .body(var buf):
            requestBody?.writeBuffer(&buf)

        case .end:
            guard let head = requestHead else { return }
            handleHTTPRequest(context: context, head: head, body: requestBody)
            requestHead = nil
            requestBody = nil
        }
    }

    // MARK: - CONNECT

    private func handleConnect(context: ChannelHandlerContext, head: HTTPRequestHead) {
        let target = parseConnectTarget(head.uri)
        let host = target.host
        let port = target.port

        // Send 200 Connection Established
        let response = HTTPResponseHead(version: .http1_1, status: .ok)
        context.write(wrapOutboundOut(.head(response)), promise: nil)
        context.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)

        if let ca = certificateAuthority, ca.isLoaded, ruleMatcher.anyRuleCouldMatchHostAndPort(host, port: port) {
            // MITM: terminate TLS with domain cert, re-parse HTTP, intercept
            setupMITM(context: context, host: host, port: port, ca: ca)
        } else {
            // Blind tunnel: no MITM needed
            setupTCPTunnel(context: context, host: host, port: port)
        }
    }

    // MARK: - MITM Setup

    private func setupMITM(context: ChannelHandlerContext, host: String, port: Int, ca: CertificateAuthority) {
        do {
            let sslContext = try ca.sslContextForDomain(host)
            let sslHandler = NIOSSLServerHandler(context: sslContext)

            // Capture what we need before removing self from the pipeline.
            let ruleMatcher = self.ruleMatcher
            let delegate = self.delegate

            // Remove self and HTTP handlers, then add TLS + HTTP + intercept handler.
            context.pipeline.removeHandler(self).flatMap {
                self.removeHTTPCodecHandlers(from: context.pipeline)
            }.flatMap {
                // Add TLS termination (presents our domain cert to the client/WebView).
                context.pipeline.addHandler(sslHandler)
            }.flatMap {
                // Re-add HTTP codec after TLS.
                context.pipeline.configureHTTPServerPipeline()
            }.flatMap {
                // Add MITM intercept handler.
                context.pipeline.addHandler(
                    MITMInterceptHandler(
                        host: host,
                        port: port,
                        ruleMatcher: ruleMatcher,
                        delegate: delegate
                    )
                )
            }.whenFailure { error in
                print("[ProxyHTTPHandler] MITM setup failed for \(host): \(error). Falling back to tunnel.")
                self.removeMITMHandlersAndTunnel(context: context, host: host, port: port)
            }
        } catch {
            print("[ProxyHTTPHandler] Failed to get SSL context for \(host): \(error). Using tunnel.")
            setupTCPTunnel(context: context, host: host, port: port)
        }
    }

    /// Fallback tunnel setup used when MITM pipeline setup fails after self has been removed.
    private func setupTCPTunnelFallback(context: ChannelHandlerContext, host: String, port: Int) {
        let bootstrap = ClientBootstrap(group: context.eventLoop)
            .channelInitializer { channel in
                channel.eventLoop.makeSucceededFuture(())
            }

        bootstrap.connect(host: host, port: port).whenComplete { result in
            switch result {
            case .success(let upstreamChannel):
                self.installTunnelHandlers(
                    clientChannel: context.channel,
                    upstreamChannel: upstreamChannel
                )
            case .failure:
                context.close(promise: nil)
            }
        }
    }

    private func removeMITMHandlersAndTunnel(
        context: ChannelHandlerContext,
        host: String,
        port: Int
    ) {
        removeMITMHandlers(from: context.pipeline).whenComplete { _ in
            self.setupTCPTunnelFallback(context: context, host: host, port: port)
        }
    }

    // MARK: - Blind TCP Tunnel

    private func setupTCPTunnel(context: ChannelHandlerContext, host: String, port: Int) {
        let bootstrap = ClientBootstrap(group: context.eventLoop)
            .channelInitializer { channel in
                channel.eventLoop.makeSucceededFuture(())
            }

        bootstrap.connect(host: host, port: port).whenComplete { result in
            switch result {
            case .success(let upstreamChannel):
                // Remove HTTP codec from the client-facing channel so it
                // becomes a raw byte pipe, then install tunnel handlers.
                self.removeHTTPHandlersAndTunnel(
                    context: context,
                    upstreamChannel: upstreamChannel
                )

            case .failure:
                context.close(promise: nil)
            }
        }
    }

    private func removeHTTPHandlersAndTunnel(
        context: ChannelHandlerContext,
        upstreamChannel: Channel
    ) {
        context.pipeline.removeHandler(self).flatMap {
            self.removeHTTPCodecHandlers(from: context.pipeline)
        }.whenComplete { _ in
            self.installTunnelHandlers(
                clientChannel: context.channel,
                upstreamChannel: upstreamChannel
            )
        }
    }

    private func installTunnelHandlers(clientChannel: Channel, upstreamChannel: Channel) {
        let clientToServer = TunnelHandler(partnerChannel: upstreamChannel)
        let serverToClient = TunnelHandler(partnerChannel: clientChannel)

        clientChannel.pipeline.addHandler(clientToServer).whenComplete { result in
            if case .failure = result {
                clientChannel.close(promise: nil)
                upstreamChannel.close(promise: nil)
                return
            }
            upstreamChannel.pipeline.addHandler(serverToClient).whenFailure { _ in
                clientChannel.close(promise: nil)
                upstreamChannel.close(promise: nil)
            }
        }
    }

    // MARK: - Regular HTTP

    private func handleHTTPRequest(
        context: ChannelHandlerContext,
        head: HTTPRequestHead,
        body: ByteBuffer?
    ) {
        guard let originalRequest = resolveUpstreamRequest(from: head) else {
            sendErrorResponse(context: context, status: .badGateway)
            return
        }

        let requestUrl = originalRequest.upstreamURL.absoluteString
        let method = "\(originalRequest.requestHead.method)"
        let requestRule = ruleMatcher.matchRequest(url: requestUrl, method: method)
        let responseRule = ruleMatcher.matchResponse(url: requestUrl, method: method)
        let requestId = (requestRule != nil || responseRule != nil) ? UUID().uuidString : nil

        guard let rule = requestRule else {
            forwardToUpstream(
                context: context,
                request: originalRequest,
                body: body,
                responseRule: responseRule,
                requestId: requestId,
                requestUrl: requestUrl
            )
            return
        }

        let requestData = buildRequestEventData(
            requestUrl: requestUrl,
            method: method,
            headers: originalRequest.requestHead.headers,
            body: body,
            includeBody: rule.includeBody
        )

        let eventLoop = context.eventLoop
        dispatchRequestIntercept(
            delegate: delegate,
            requestId: requestId ?? UUID().uuidString,
            ruleName: rule.ruleName,
            requestData: requestData,
            eventLoop: eventLoop
        ) { [weak self] modifications in
            guard let self else { return }
            var modifiedHead = originalRequest.requestHead
            var modifiedBody = body
            applyRequestModifications(
                modifications,
                to: &modifiedHead,
                body: &modifiedBody,
                allocator: context.channel.allocator
            )

            guard let resolvedRequest = resolveUpstreamRequest(
                from: modifiedHead,
                defaultScheme: originalRequest.upstreamURL.scheme,
                defaultHost: originalRequest.upstreamURL.host,
                defaultPortValue: originalRequest.upstreamURL.port ?? defaultPort(for: originalRequest.upstreamURL.scheme ?? "")
            ) else {
                self.sendErrorResponse(context: context, status: .badGateway)
                return
            }

            let finalRequestUrl = resolvedRequest.upstreamURL.absoluteString
            let finalResponseRule = self.ruleMatcher.matchResponse(
                url: finalRequestUrl,
                method: "\(resolvedRequest.requestHead.method)"
            )

            self.forwardToUpstream(
                context: context,
                request: resolvedRequest,
                body: modifiedBody,
                responseRule: finalResponseRule,
                requestId: requestId,
                requestUrl: finalRequestUrl
            )
        }
    }

    // MARK: - Upstream Forwarding

    private func forwardToUpstream(
        context: ChannelHandlerContext,
        request: ResolvedUpstreamRequest,
        body: ByteBuffer?,
        responseRule: NativeProxyRule?,
        requestId: String?,
        requestUrl: String
    ) {
        guard let host = request.upstreamURL.host else {
            sendErrorResponse(context: context, status: .badGateway)
            return
        }

        let port = request.upstreamURL.port ?? defaultPort(for: request.upstreamURL.scheme ?? "http") ?? 80

        let bootstrap = ClientBootstrap(group: context.eventLoop)
            .channelInitializer { channel in
                let handlers: [ChannelHandler] = [
                    HTTPRequestEncoder(),
                    ByteToMessageHandler(HTTPResponseDecoder(leftOverBytesStrategy: .forwardBytes))
                ]
                return channel.pipeline.addHandlers(handlers)
            }

        bootstrap.connect(host: host, port: port).whenComplete { [weak self] result in
            guard let self else {
                context.close(promise: nil)
                return
            }
            handleUpstreamConnection(
                result,
                forward: UpstreamForwardContext(
                    clientContext: context,
                    delegate: self.delegate,
                    responseRule: responseRule,
                    requestId: requestId,
                    requestUrl: requestUrl,
                    requestHead: request.requestHead,
                    body: body
                ),
                onFailure: { self.sendErrorResponse(context: context, status: .badGateway) }
            )
        }
    }

    private func sendErrorResponse(
        context: ChannelHandlerContext,
        status: HTTPResponseStatus
    ) {
        let response = HTTPResponseHead(version: .http1_1, status: status)
        context.write(wrapOutboundOut(.head(response)), promise: nil)
        context.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)
    }

    func errorCaught(context: ChannelHandlerContext, error _: Error) {
        context.close(promise: nil)
    }

    private func removeHTTPCodecHandlers(from pipeline: ChannelPipeline) -> EventLoopFuture<Void> {
        removeHandlerIfPresent(HTTPServerPipelineHandler.self, from: pipeline)
            .flatMap {
                self.removeHandlerIfPresent(ByteToMessageHandler<HTTPRequestDecoder>.self, from: pipeline)
            }
            .flatMap {
                self.removeHandlerIfPresent(HTTPResponseEncoder.self, from: pipeline)
            }
    }

    private func removeMITMHandlers(from pipeline: ChannelPipeline) -> EventLoopFuture<Void> {
        removeHandlerIfPresent(MITMInterceptHandler.self, from: pipeline)
            .flatMap {
                self.removeHandlerIfPresent(NIOSSLServerHandler.self, from: pipeline)
            }
            .flatMap {
                self.removeHTTPCodecHandlers(from: pipeline)
            }
    }

    private func removeHandlerIfPresent<Handler: RemovableChannelHandler>(
        _ type: Handler.Type,
        from pipeline: ChannelPipeline
    ) -> EventLoopFuture<Void> {
        pipeline.handler(type: type)
            .flatMap { pipeline.removeHandler($0) }
            .flatMapError { _ in pipeline.eventLoop.makeSucceededFuture(()) }
    }
}

// MARK: - MITMInterceptHandler

/// Handles decrypted HTTP traffic after MITM TLS termination.
///
/// This handler operates on the plaintext side of the TLS connection.
/// It reconstructs full HTTPS URLs, matches rules, fires interception events,
/// and forwards requests upstream via TLS to the real server.
final class MITMInterceptHandler: ChannelInboundHandler, RemovableChannelHandler {
    typealias InboundIn = HTTPServerRequestPart
    typealias OutboundOut = HTTPServerResponsePart

    private let host: String
    private let port: Int
    private let ruleMatcher: ProxyRuleMatcher
    private weak var delegate: ProxyEventDelegate?

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    init(host: String, port: Int, ruleMatcher: ProxyRuleMatcher, delegate: ProxyEventDelegate?) {
        self.host = host
        self.port = port
        self.ruleMatcher = ruleMatcher
        self.delegate = delegate
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let part = unwrapInboundIn(data)
        switch part {
        case .head(let head):
            requestHead = head
            requestBody = context.channel.allocator.buffer(capacity: 0)
        case .body(var buf):
            requestBody?.writeBuffer(&buf)
        case .end:
            guard let head = requestHead else { return }
            handleDecryptedRequest(context: context, head: head, body: requestBody)
            requestHead = nil
            requestBody = nil
        }
    }

    private func handleDecryptedRequest(
        context: ChannelHandlerContext,
        head: HTTPRequestHead,
        body: ByteBuffer?
    ) {
        guard let originalRequest = resolveUpstreamRequest(
            from: head,
            defaultScheme: "https",
            defaultHost: self.host,
            defaultPortValue: self.port
        ) else {
            let errResp = HTTPResponseHead(version: .http1_1, status: .badGateway)
            context.write(NIOAny(HTTPServerResponsePart.head(errResp)), promise: nil)
            context.writeAndFlush(NIOAny(HTTPServerResponsePart.end(nil)), promise: nil)
            return
        }

        let requestUrl = originalRequest.upstreamURL.absoluteString
        let method = "\(originalRequest.requestHead.method)"
        let requestRule = ruleMatcher.matchRequest(url: requestUrl, method: method)
        let responseRule = ruleMatcher.matchResponse(url: requestUrl, method: method)
        let requestId = (requestRule != nil || responseRule != nil) ? UUID().uuidString : nil

        guard let rule = requestRule else {
            forwardUpstreamTLS(
                context: context,
                request: originalRequest,
                body: body,
                responseRule: responseRule,
                requestId: requestId,
                requestUrl: requestUrl
            )
            return
        }
        let requestData = buildRequestEventData(
            requestUrl: requestUrl,
            method: method,
            headers: originalRequest.requestHead.headers,
            body: body,
            includeBody: rule.includeBody
        )

        let eventLoop = context.eventLoop
        dispatchRequestIntercept(
            delegate: delegate,
            requestId: requestId ?? UUID().uuidString,
            ruleName: rule.ruleName,
            requestData: requestData,
            eventLoop: eventLoop
        ) { [weak self] modifications in
            guard let self else { return }
            var modifiedHead = originalRequest.requestHead
            var modifiedBody = body
            applyRequestModifications(
                modifications,
                to: &modifiedHead,
                body: &modifiedBody,
                allocator: context.channel.allocator
            )
            guard let resolvedRequest = resolveUpstreamRequest(
                from: modifiedHead,
                defaultScheme: "https",
                defaultHost: self.host,
                defaultPortValue: self.port
            ) else {
                let errResp = HTTPResponseHead(version: .http1_1, status: .badGateway)
                context.write(NIOAny(HTTPServerResponsePart.head(errResp)), promise: nil)
                context.writeAndFlush(NIOAny(HTTPServerResponsePart.end(nil)), promise: nil)
                return
            }
            let finalRequestUrl = resolvedRequest.upstreamURL.absoluteString
            let finalResponseRule = self.ruleMatcher.matchResponse(
                url: finalRequestUrl,
                method: "\(resolvedRequest.requestHead.method)"
            )
            self.forwardUpstreamTLS(
                context: context,
                request: resolvedRequest,
                body: modifiedBody,
                responseRule: finalResponseRule,
                requestId: requestId,
                requestUrl: finalRequestUrl
            )
        }
    }

    private func forwardUpstreamTLS(
        context: ChannelHandlerContext,
        request: ResolvedUpstreamRequest,
        body: ByteBuffer?,
        responseRule: NativeProxyRule?,
        requestId: String?,
        requestUrl: String
    ) {
        guard let upstreamHost = request.upstreamURL.host else {
            let errResp = HTTPResponseHead(version: .http1_1, status: .badGateway)
            context.write(NIOAny(HTTPServerResponsePart.head(errResp)), promise: nil)
            context.writeAndFlush(NIOAny(HTTPServerResponsePart.end(nil)), promise: nil)
            return
        }
        let upstreamPort = request.upstreamURL.port ?? defaultPort(for: request.upstreamURL.scheme ?? "https") ?? self.port

        let bootstrap = ClientBootstrap(group: context.eventLoop)
            .channelInitializer { channel in
                do {
                    // Add TLS client handler (trusts system CA store).
                    let tlsConfig = TLSConfiguration.makeClientConfiguration()
                    let sslContext = try NIOSSLContext(configuration: tlsConfig)
                    let sslHandler = try NIOSSLClientHandler(
                        context: sslContext,
                        serverHostname: upstreamHost
                    )
                    return channel.pipeline.addHandler(sslHandler).flatMap {
                        channel.pipeline.addHandlers([
                            HTTPRequestEncoder(),
                            ByteToMessageHandler(HTTPResponseDecoder(leftOverBytesStrategy: .forwardBytes))
                        ])
                    }
                } catch {
                    return channel.eventLoop.makeFailedFuture(error)
                }
            }

        bootstrap.connect(host: upstreamHost, port: upstreamPort).whenComplete { [weak self] result in
            guard let self else {
                context.close(promise: nil)
                return
            }
            handleUpstreamConnection(
                result,
                forward: UpstreamForwardContext(
                    clientContext: context,
                    delegate: self.delegate,
                    responseRule: responseRule,
                    requestId: requestId,
                    requestUrl: requestUrl,
                    requestHead: request.requestHead,
                    body: body
                ),
                onFailure: {
                    let errResp = HTTPResponseHead(version: .http1_1, status: .badGateway)
                    context.write(NIOAny(HTTPServerResponsePart.head(errResp)), promise: nil)
                    context.writeAndFlush(NIOAny(HTTPServerResponsePart.end(nil)), promise: nil)
                }
            )
        }
    }

    func errorCaught(context: ChannelHandlerContext, error _: Error) {
        context.close(promise: nil)
    }
}

// MARK: - UpstreamResponseHandler

/// Reads the HTTP response from the upstream server and relays it back to
/// the client (WKWebView), optionally firing a response interception event.
final class UpstreamResponseHandler: ChannelInboundHandler {
    typealias InboundIn = HTTPClientResponsePart

    private let clientContext: ChannelHandlerContext
    private let rule: NativeProxyRule?
    private weak var delegate: ProxyEventDelegate?
    private let requestUrl: String
    private let requestId: String?

    private var responseHead: HTTPResponseHead?
    private var responseBody: ByteBuffer?

    init(clientContext: ChannelHandlerContext,
         rule: NativeProxyRule?,
         delegate: ProxyEventDelegate?,
         requestUrl: String,
         requestId: String?) {
        self.clientContext = clientContext
        self.rule = rule
        self.delegate = delegate
        self.requestUrl = requestUrl
        self.requestId = requestId
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let part = unwrapInboundIn(data)

        switch part {
        case .head(let head):
            responseHead = head
            responseBody = context.channel.allocator.buffer(capacity: 0)

        case .body(var buf):
            responseBody?.writeBuffer(&buf)

        case .end:
            guard let head = responseHead else { return }
            handleResponse(head: head, body: responseBody)
            context.close(promise: nil)
        }
    }

    private func handleResponse(head: HTTPResponseHead, body: ByteBuffer?) {
        guard let rule = rule, rule.interceptsResponse else {
            sendToClient(head: head, body: body)
            return
        }

        let requestId = self.requestId ?? UUID().uuidString

        let responseData = buildResponseEventData(
            requestUrl: requestUrl,
            head: head,
            body: body,
            includeBody: rule.includeBody
        )

        dispatchResponseIntercept(
            delegate: delegate,
            requestId: requestId,
            ruleName: rule.ruleName,
            responseData: responseData,
            eventLoop: clientContext.eventLoop
        ) { [weak self] modifications in
            guard let self else { return }
            var modifiedHead = head
            var modifiedBody = body
            applyResponseModifications(
                modifications,
                to: &modifiedHead,
                body: &modifiedBody,
                allocator: self.clientContext.channel.allocator
            )
            self.sendToClient(head: modifiedHead, body: modifiedBody)
        }
    }

    private func sendToClient(head: HTTPResponseHead, body: ByteBuffer?) {
        clientContext.write(
            NIOAny(HTTPServerResponsePart.head(head)), promise: nil)
        if let body = body, body.readableBytes > 0 {
            clientContext.write(
                NIOAny(HTTPServerResponsePart.body(.byteBuffer(body))), promise: nil)
        }
        clientContext.writeAndFlush(
            NIOAny(HTTPServerResponsePart.end(nil)), promise: nil)
    }

    func errorCaught(context: ChannelHandlerContext, error _: Error) {
        context.close(promise: nil)
    }
}

// MARK: - TunnelHandler

/// Simple bidirectional TCP tunnel handler for CONNECT passthrough.
/// Each side of the tunnel has one instance; it reads bytes and writes
/// them to the partner channel.
final class TunnelHandler: ChannelInboundHandler {
    typealias InboundIn = ByteBuffer
    typealias OutboundOut = ByteBuffer

    private let partnerChannel: Channel

    init(partnerChannel: Channel) {
        self.partnerChannel = partnerChannel
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let buffer = unwrapInboundIn(data)
        partnerChannel.writeAndFlush(NIOAny(buffer), promise: nil)
    }

    func channelInactive(context _: ChannelHandlerContext) {
        partnerChannel.close(promise: nil)
    }

    func errorCaught(context: ChannelHandlerContext, error _: Error) {
        context.close(promise: nil)
    }
}
#endif
