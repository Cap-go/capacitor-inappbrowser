import XCTest
@testable import InappbrowserPlugin

final class ProxySchemeRedirectSupportTests: XCTestCase {
    func testProxySchemeTimeoutResolutionActionFallsBackForPendingRedirects() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(
                phase: "inbound",
                hasCachedResponse: true,
                hasPendingRedirect: true
            ),
            .fallbackToNative
        )
    }

    func testProxySchemeJsResponseResolutionActionFinishesCachedResponsesBeforeReentry() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "inbound", hasCachedResponse: true),
            .finishCachedResponse
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "outbound", hasCachedResponse: true),
            .finishCachedResponse
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "outbound", hasCachedResponse: false),
            .executeNativePipeline
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "inbound", hasCachedResponse: false),
            .executeInboundDecision
        )
    }
}
