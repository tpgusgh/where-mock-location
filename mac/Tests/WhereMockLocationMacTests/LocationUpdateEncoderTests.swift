import XCTest
@testable import WhereMockLocationMac

final class LocationUpdateEncoderTests: XCTestCase {
    func testEncodeProducesValidJSONWithCorrectFields() throws {
        let json = LocationUpdateEncoder.encode(token: "abc123", lat: 37.5665, lng: 126.9780)
        let data = try XCTUnwrap(json.data(using: .utf8))
        let obj = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(obj["token"] as? String, "abc123")
        XCTAssertEqual(obj["lat"] as? Double, 37.5665)
        XCTAssertEqual(obj["lng"] as? Double, 126.9780)
    }

    func testDecodeResponseParsesOkStatus() {
        let result = LocationUpdateEncoder.decodeResponse(#"{"status":"ok","message":"location updated"}"#)
        XCTAssertEqual(result, .ok(message: "location updated"))
    }

    func testDecodeResponseParsesErrorStatus() {
        let result = LocationUpdateEncoder.decodeResponse(#"{"status":"error","message":"invalid token"}"#)
        XCTAssertEqual(result, .error(message: "invalid token"))
    }

    func testDecodeResponseReturnsNilForMalformedJSON() {
        XCTAssertNil(LocationUpdateEncoder.decodeResponse("not json"))
    }

    func testDecodeResponseReturnsNilWhenFieldsMissing() {
        XCTAssertNil(LocationUpdateEncoder.decodeResponse(#"{"status":"ok"}"#))
    }
}
