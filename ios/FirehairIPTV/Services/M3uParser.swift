import Foundation

class M3uParser {
    static func parse(content: String) -> [Channel] {
        var channels: [Channel] = []
        let lines = content.components(separatedBy: .newlines)
        
        var currentName = "Canal"
        var currentGroup = "Geral"
        var currentLogo: String? = nil
        var currentTvgId: String? = nil
        
        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.hasPrefix("#EXTINF:") {
                if let groupRange = trimmed.range(of: "group-title=\"") {
                    let substring = trimmed[groupRange.upperBound...]
                    if let endQuote = substring.firstIndex(of: "\"") {
                        currentGroup = String(substring[..<endQuote])
                    }
                }
                if let logoRange = trimmed.range(of: "tvg-logo=\"") {
                    let substring = trimmed[logoRange.upperBound...]
                    if let endQuote = substring.firstIndex(of: "\"") {
                        currentLogo = String(substring[..<endQuote])
                    }
                }
                if let commaIndex = trimmed.firstIndex(of: ",") {
                    let nameSubstring = trimmed[trimmed.index(after: commaIndex)...]
                    currentName = String(nameSubstring).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            } else if !trimmed.isEmpty && !trimmed.hasPrefix("#") {
                let channel = Channel(
                    id: UUID().uuidString,
                    name: currentName,
                    group: currentGroup,
                    logoUrl: currentLogo,
                    streamUrl: trimmed,
                    tvgId: currentTvgId,
                    isFavorite: false,
                    isWatched: false,
                    lastWatchedTimestamp: 0
                )
                channels.append(channel)
                currentName = "Canal"
                currentGroup = "Geral"
                currentLogo = nil
                currentTvgId = nil
            }
        }
        return channels
    }
}
