import XCTest
@testable import WhereMockLocationMac

final class PairingTargetTests: XCTestCase {
    func testIsCompleteTrueWhenAllFieldsPresentAndPortNumeric() {
        let target = PairingTarget(ip: "192.168.1.5", port: "8765", token: "abc")
        XCTAssertTrue(target.isComplete)
    }

    func testIsCompleteFalseWhenIPBlank() {
        let target = PairingTarget(ip: "  ", port: "8765", token: "abc")
        XCTAssertFalse(target.isComplete)
    }

    func testIsCompleteFalseWhenPortNotNumeric() {
        let target = PairingTarget(ip: "192.168.1.5", port: "abc", token: "abc")
        XCTAssertFalse(target.isComplete)
    }

    func testIsCompleteFalseWhenTokenBlank() {
        let target = PairingTarget(ip: "192.168.1.5", port: "8765", token: "")
        XCTAssertFalse(target.isComplete)
    }

    func testURLConstructsWebSocketURLWhenComplete() {
        let target = PairingTarget(ip: "192.168.1.5", port: "8765", token: "abc")
        XCTAssertEqual(target.url?.absoluteString, "ws://192.168.1.5:8765")
    }

    func testURLNilWhenIncomplete() {
        let target = PairingTarget(ip: "", port: "8765", token: "abc")
        XCTAssertNil(target.url)
    }
}
