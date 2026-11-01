import Foundation

class XtreamService {
    func cleanServerUrl(_ rawUrl: String) -> String {
        var trimmed = rawUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.lowercased().hasPrefix("http://") && !trimmed.lowercased().hasPrefix("https://") {
            trimmed = "http://\(trimmed)"
        }
        while trimmed.hasSuffix("/") {
            trimmed.removeLast()
        }
        return trimmed
    }
    
    func fetchChannels(serverUrl: String, username: String, password: String) async throws -> (XtreamAccountInfo, [Channel]) {
        let cleanUrl = cleanServerUrl(serverUrl)
        let authUrlString = "\(cleanUrl)/player_api.php?username=\(username)&password=\(password)"
        
        guard let authUrl = URL(string: authUrlString) else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: authUrl)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let userInfo = json["user_info"] as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }
        
        let status = userInfo["status"] as? String ?? "Active"
        let maxConn = userInfo["max_connections"] as? String ?? "1"
        let accountInfo = XtreamAccountInfo(status: status == "Active" ? "Ativa" : status, expDateFormatted: "Ilimitado", maxConnections: maxConn)
        
        // Fetch Categories
        var categoriesMap: [String: String] = [:]
        if let catUrl = URL(string: "\(cleanUrl)/player_api.php?username=\(username)&password=\(password)&action=get_live_categories") {
            if let (catData, _) = try? await URLSession.shared.data(from: catUrl),
               let catArray = try? JSONSerialization.jsonObject(with: catData) as? [[String: Any]] {
                for item in catArray {
                    if let catId = item["category_id"] as? String, let catName = item["category_name"] as? String {
                        categoriesMap[catId] = catName
                    }
                }
            }
        }
        
        // Fetch Live Streams
        guard let streamsUrl = URL(string: "\(cleanUrl)/player_api.php?username=\(username)&password=\(password)&action=get_live_streams") else {
            throw URLError(.badURL)
        }
        
        let (streamsData, _) = try await URLSession.shared.data(from: streamsUrl)
        guard let streamsArray = try JSONSerialization.jsonObject(with: streamsData) as? [[String: Any]] else {
            throw URLError(.cannotParseResponse)
        }
        
        var channels: [Channel] = []
        for item in streamsArray {
            let streamId = item["stream_id"] as? Int ?? (Int(item["stream_id"] as? String ?? "") ?? 0)
            if streamId == 0 { continue }
            let name = item["name"] as? String ?? "Canal \(streamId)"
            let categoryId = item["category_id"] as? String ?? ""
            let icon = item["stream_icon"] as? String
            let epgId = item["epg_channel_id"] as? String
            let group = categoriesMap[categoryId] ?? "Canais Ao Vivo"
            let streamUrl = "\(cleanUrl)/live/\(username)/\(password)/\(streamId).m3u8"
            
            channels.append(Channel(
                id: "xtream_\(streamId)",
                name: name,
                group: group,
                logoUrl: icon,
                streamUrl: streamUrl,
                tvgId: epgId,
                isFavorite: false,
                isWatched: false,
                lastWatchedTimestamp: 0
            ))
        }
        
        return (accountInfo, channels)
    }
}
