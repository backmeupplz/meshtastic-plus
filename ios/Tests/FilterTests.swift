import XCTest
@testable import MeshPlus

final class FilterTests: XCTestCase {
    func testMasksWholeWordsOnly() {
        Mesh.shared.setFilter(true)
        XCTAssertEqual(clean("What the FUCK, you bitch"), "What the ••••, you •••••")
        XCTAssertEqual(clean("class Scunthorpe cocktail assume"), "class Scunthorpe cocktail assume")
        XCTAssertEqual(clean("привет 👋 shit"), "привет 👋 ••••")
        Mesh.shared.setFilter(false)
        XCTAssertEqual(clean("shit"), "shit")
        Mesh.shared.setFilter(true)
    }
}
