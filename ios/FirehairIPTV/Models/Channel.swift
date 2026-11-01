import Foundation

struct Channel: Identifiable, Codable, Hashable {
    let id: String
    let name: String
    let group: String
    let logoUrl: String?
    let streamUrl: String
    let tvgId: String?
    var isFavorite: Bool
    var isWatched: Bool
    var lastWatchedTimestamp: Double
}

enum ConnectionType: String, Codable {
    case m3u
    case xtreamCodes
}

struct XtreamCredentials: Codable {
    let serverUrl: String
    let username: String
    let password: String
}

struct XtreamAccountInfo: Codable {
    let status: String
    let expDateFormatted: String?
    let maxConnections: String
}

struct PlaylistInfo: Codable {
    let url: String
    let channelCount: Int
    let connectionType: ConnectionType
    let xtreamAccountInfo: XtreamAccountInfo?
}
