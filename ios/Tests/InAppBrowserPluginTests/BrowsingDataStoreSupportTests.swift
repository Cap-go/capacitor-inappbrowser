import XCTest
import WebKit
@testable import InappbrowserPlugin

final class BrowsingDataStoreSupportTests: XCTestCase {
    func testHostAppDefaultStoreIsDetected() {
        XCTAssertTrue(
            BrowsingDataStoreSupport.isHostAppWebsiteDataStore(.default())
        )
    }

    func testNonPersistentStoreIsNotHostAppStore() {
        XCTAssertFalse(
            BrowsingDataStoreSupport.isHostAppWebsiteDataStore(.nonPersistent())
        )
    }

    func testClearAllBrowsingDataExcludesHostAppDefaultStore() {
        let stores = BrowsingDataStoreSupport.storesForClearAllBrowsingData(
            openStores: [.default(), .nonPersistent()]
        )

        XCTAssertFalse(stores.contains { BrowsingDataStoreSupport.isHostAppWebsiteDataStore($0) })
        XCTAssertTrue(stores.contains { !$0.isPersistent })
    }

    func testFallbackStoresWhenNoWebViewOpenNeverReturnsHostDefaultOnModernOS() {
        let stores = BrowsingDataStoreSupport.fallbackStoresWhenNoWebViewOpen()
        XCTAssertFalse(stores.contains { BrowsingDataStoreSupport.isHostAppWebsiteDataStore($0) })
    }

    func testPersistFalseUsesNonPersistentStore() {
        let store = BrowsingDataStoreSupport.websiteDataStore(persistWebViewData: false)
        XCTAssertFalse(store.isPersistent)
    }
}
