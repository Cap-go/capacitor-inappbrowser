import Foundation
import NIO
import NIOCore
import NIOHTTP1

// MARK: - ProxyHTTPHandler

/// Handles incoming HTTP connections from the WKWebView.
///
/// For `CONNECT` requests it establishes a blind TCP tunnel.
/// For regular HTTP requests it matches rules, optionally fires
/// interception events, and forwards to the upstream server.
final class ProxyHTTPHandler: ChannelInboundHandler, RemovableChannelHandler {
    typealias InboundIn = HTTPServerRequestPart
    typealias OutboundOut = HTTPServerResponsePart

    private let ruleMatcher: ProxyRuleMatcher
    private weak var delegate: ProxyEventDelegate?

    private var requestHead: HTTPRequestHead?
    private var requestBody: ByteBuffer?

    init(ruleMatcher: ProxyRuleMatcher, delegate: ProxyEventDelegate?) {
        self.ruleMatcher = ruleMatcher
        self.delegate = delegate
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

    // MARK: - CONNECT (blind tunnel)

    private func handleConnect(context: ChannelHandlerContext, head: HTTPRequestHead) {
        let components = head.uri.split(separator: ":")
        let host = String(components.first ?? "")
        let port = Int(components.last ?? "443") ?? 443

        // Send 200 Connection Established
        let response = HTTPResponseHead(version: .http1_1, status: .ok)
        context.write(wrapOutboundOut(.head(response)), promise: nil)
        context.writeAndFlush(wrapOutboundOut(.end(nil)), promise: nil)

        // TODO: When cert generation is complete, do MITM for rule-matched hosts.
        // For now, always use a blind TCP tunnel.
        setupTCPTunnel(context: context, host: host, port: port)
    }

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
        // Remove ourselves first
        context.pipeline.removeHandler(self).whenComplete { _ in
            // Remove the HTTP server pipeline handler if present
            context.pipeline.handler(type: HTTPServerPipelineHandler.self).whenComplete { result in
                if case .success(let handler) = result {
                    context.pipeline.removeHandler(handler).whenComplete { _ in
                        self.installTunnelHandlers(
                            clientChannel: context.channel,
                            upstreamChannel: upstreamChannel
                        )
                    }
                } else {
                    // No pipeline handler to remove — install tunnel anyway
                    self.installTunnelHandlers(
                        clientChannel: context.channel,
                        upstreamChannel: upstreamChannel
                    )
                }
            }
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
        let url = head.uri
        let method = "\(head.method)"

        guard let rule = ruleMatcher.match(url: url, method: method),
              rule.interceptsRequest else {
            // No matching rule — forward directly.
            forwardToUpstream(context: context, head: head, body: body, rule: nil)
            return
        }

        let requestId = UUID().uuidString

        // Build the request data dictionary for the JS event.
        var requestData: [String: Any] = [
            "url": url,
            "method": method,
            "headers": Dictionary(
                head.headers.map { ($0.name, $0.value) },
                uniquingKeysWith: { _, last in last }
            )
        ]
        if rule.includeBody, let body = body, body.readableBytes > 0 {
            let data = body.getData(at: body.readerIndex, length: body.readableBytes) ?? Data()
            requestData["body"] = data.base64EncodedString()
        }

        // Fire the interception event and wait for a response from JS.
        let eventLoop = context.eventLoop
        delegate?.onRequestIntercept(
            requestId: requestId,
            ruleIndex: rule.ruleIndex,
            requestData: requestData
        ) { [weak self] modifications in
            eventLoop.execute {
                var modifiedHead = head
                var modifiedBody = body

                if let mods = modifications {
                    if let newUrl = mods["url"] as? String {
                        modifiedHead.uri = newUrl
                    }
                    if let newMethod = mods["method"] as? String {
                        modifiedHead.method = HTTPMethod(rawValue: newMethod)
                    }
                    if let newHeaders = mods["headers"] as? [String: String] {
                        modifiedHead.headers = HTTPHeaders(
                            newHeaders.map { ($0.key, $0.value) }
                        )
                    }
                    if let newBodyB64 = mods["body"] as? String,
                       let bodyData = Data(base64Encoded: newBodyB64) {
                        modifiedBody = context.channel.allocator.buffer(data: bodyData)
                        modifiedHead.headers.replaceOrAdd(
                            name: "content-length",
                            value: "\(bodyData.count)"
                        )
                    }
                }

                self?.forwardToUpstream(
                    context: context,
                    head: modifiedHead,
                    body: modifiedBody,
                    rule: rule
                )
            }
        }
    }

    // MARK: - Upstream Forwarding

    private func forwardToUpstream(
        context: ChannelHandlerContext,
        head: HTTPRequestHead,
        body: ByteBuffer?,
        rule: NativeProxyRule?
    ) {
        guard let url = URL(string: head.uri), let host = url.host else {
            sendErrorResponse(context: context, status: .badGateway)
            return
        }

        let port = url.port ?? (url.scheme == "https" ? 443 : 80)

        let bootstrap = ClientBootstrap(group: context.eventLoop)
            .channelInitializer { channel in
                let handlers: [ChannelHandler] = [
                    HTTPRequestEncoder(),
                    ByteToMessageHandler(HTTPResponseDecoder(leftOverBytesStrategy: .forwardBytes))
                ]
                return channel.pipeline.addHandlers(handlers)
            }

        bootstrap.connect(host: host, port: port).whenComplete { [weak self] result in
            switch result {
            case .success(let upstreamChannel):
                // Send the request upstream.
                upstreamChannel.write(
                    NIOAny(HTTPClientRequestPart.head(head)), promise: nil)
                if let body = body, body.readableBytes > 0 {
                    upstreamChannel.write(
                        NIOAny(HTTPClientRequestPart.body(.byteBuffer(body))), promise: nil)
                }
                upstreamChannel.writeAndFlush(
                    NIOAny(HTTPClientRequestPart.end(nil)), promise: nil)

                // Read the response and relay back to the client.
                let responseHandler = UpstreamResponseHandler(
                    clientContext: context,
                    rule: rule,
                    delegate: self?.delegate,
                    requestUrl: head.uri
                )
                upstreamChannel.pipeline.addHandler(responseHandler).whenFailure { _ in
                    context.close(promise: nil)
                }

            case .failure:
                self?.sendErrorResponse(context: context, status: .badGateway)
            }
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

    func errorCaught(context: ChannelHandlerContext, error: Error) {
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

    private var responseHead: HTTPResponseHead?
    private var responseBody: ByteBuffer?

    init(clientContext: ChannelHandlerContext,
         rule: NativeProxyRule?,
         delegate: ProxyEventDelegate?,
         requestUrl: String) {
        self.clientContext = clientContext
        self.rule = rule
        self.delegate = delegate
        self.requestUrl = requestUrl
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
            handleResponse(upstreamContext: context, head: head, body: responseBody)
            context.close(promise: nil)
        }
    }

    private func handleResponse(
        upstreamContext: ChannelHandlerContext,
        head: HTTPResponseHead,
        body: ByteBuffer?
    ) {
        guard let rule = rule, rule.interceptsResponse else {
            sendToClient(head: head, body: body)
            return
        }

        let requestId = UUID().uuidString

        var responseData: [String: Any] = [
            "url": requestUrl,
            "status": Int(head.status.code),
            "headers": Dictionary(
                head.headers.map { ($0.name, $0.value) },
                uniquingKeysWith: { _, last in last }
            )
        ]
        if rule.includeBody, let body = body, body.readableBytes > 0 {
            let data = body.getData(at: body.readerIndex, length: body.readableBytes) ?? Data()
            responseData["body"] = data.base64EncodedString()
        }

        delegate?.onResponseIntercept(
            requestId: requestId,
            ruleIndex: rule.ruleIndex,
            responseData: responseData
        ) { [weak self] modifications in
            self?.clientContext.eventLoop.execute {
                var modifiedHead = head
                var modifiedBody = body

                if let mods = modifications {
                    if let status = mods["status"] as? Int {
                        modifiedHead.status = HTTPResponseStatus(statusCode: status)
                    }
                    if let newHeaders = mods["headers"] as? [String: String] {
                        modifiedHead.headers = HTTPHeaders(
                            newHeaders.map { ($0.key, $0.value) }
                        )
                    }
                    if let newBodyB64 = mods["body"] as? String,
                       let bodyData = Data(base64Encoded: newBodyB64) {
                        modifiedBody = self?.clientContext.channel.allocator.buffer(data: bodyData)
                        modifiedHead.headers.replaceOrAdd(
                            name: "content-length",
                            value: "\(bodyData.count)"
                        )
                    }
                }

                self?.sendToClient(head: modifiedHead, body: modifiedBody)
            }
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

    func errorCaught(context: ChannelHandlerContext, error: Error) {
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

    func channelInactive(context: ChannelHandlerContext) {
        partnerChannel.close(promise: nil)
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        context.close(promise: nil)
    }
}
