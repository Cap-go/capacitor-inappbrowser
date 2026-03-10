//
//  Enums.swift
//  Sample
//
//  Created by Meniny on 2018-01-20.
//  Copyright © 2018年 Meniny. All rights reserved.
//

import Foundation
import UIKit

public enum WKWebSource: Equatable {
    case remote(URL)
    case file(URL, access: URL)
    case string(String, base: URL?)

    public var url: URL? {
        switch self {
        case .remote(let urlValue): return urlValue
        case .file(let urlValue, access: _): return urlValue
        default: return nil
        }
    }

    public var remoteURL: URL? {
        switch self {
        case .remote(let urlValue): return urlValue
        default: return nil
        }
    }

    public var absoluteString: String? {
        switch self {
        case .remote(let urlValue): return urlValue.absoluteString
        case .file(let urlValue, access: _): return urlValue.absoluteString
        default: return nil
        }
    }
}

public enum BarButtonItemType {
    case back
    case forward
    case reload
    case screenshot
    case stop
    case activity
    case done
    case flexibleSpace
    case custom(icon: UIImage?, title: String?, action: (WKWebViewController) -> Void)
}

public enum NavigationBarPosition: String, Equatable, Codable {
    case none
    case left
    case right
}

@objc public enum NavigationType: Int, Equatable, Codable {
    case linkActivated
    case formSubmitted
    case backForward
    case reload
    case formResubmitted
    case other
}
