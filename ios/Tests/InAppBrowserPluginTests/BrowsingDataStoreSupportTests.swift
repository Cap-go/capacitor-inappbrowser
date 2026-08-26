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

    func testUseSharedDataStoreReturnsHostDefaultStore() {
        let store = BrowsingDataStoreSupport.websiteDataStore(
            persistWebViewData: true,
            useSharedDataStore: true
        )
        XCTAssertTrue(BrowsingDataStoreSupport.isHostAppWebsiteDataStore(store))
        XCTAssertTrue(store.isPersistent)
    }

    func testUseSharedDataStoreIgnoredWhenPersistFalse() {
        let store = BrowsingDataStoreSupport.websiteDataStore(
            persistWebViewData: false,
            useSharedDataStore: true
        )
        XCTAssertFalse(store.isPersistent)
        XCTAssertFalse(BrowsingDataStoreSupport.isHostAppWebsiteDataStore(store))
    }

    func testOpenTimeClearingUsesCookiesOnlyWhenRequested() {
        let dataTypes = BrowsingDataStoreSupport.websiteDataTypesForOpenTimeClearing(
            clearCookies: true,
            clearCache: false
        )
        XCTAssertEqual(dataTypes, Set([WKWebsiteDataTypeCookies]))
    }

    func testOpenTimeClearingUsesCacheTypesWhenRequested() {
        let dataTypes = BrowsingDataStoreSupport.websiteDataTypesForOpenTimeClearing(
            clearCookies: false,
            clearCache: true
        )
        XCTAssertEqual(
            dataTypes,
            Set([WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache])
        )
    }

    func testOpenTimeClearingUsesNoTypesWhenDisabled() {
        let dataTypes = BrowsingDataStoreSupport.websiteDataTypesForOpenTimeClearing(
            clearCookies: false,
            clearCache: false
        )
        XCTAssertTrue(dataTypes.isEmpty)
    }

    func testDefaultIsolatedStoreIsNotHostAppStoreOnModernOS() {
        let store = BrowsingDataStoreSupport.websiteDataStore(
            persistWebViewData: true,
            useSharedDataStore: false
        )
        if #available(iOS 17.0, *) {
            XCTAssertFalse(BrowsingDataStoreSupport.isHostAppWebsiteDataStore(store))
        } else {
            // Pre-iOS 17 falls back to the shared default store.
            XCTAssertTrue(BrowsingDataStoreSupport.isHostAppWebsiteDataStore(store))
        }
    }
}
