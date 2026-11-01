import SwiftUI

struct HomeScreenView: View {
    @State private var channels: [Channel] = []
    @State private var searchQuery: String = ""
    @State private var selectedCategory: String = "Todos"
    @State private var selectedChannel: Channel? = nil
    @State private var isShowingSettings: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var playlistInfo: PlaylistInfo? = nil
    
    var categories: [String] {
        let groups = Array(Set(channels.map { $0.group })).sorted()
        return ["Todos", "Favoritos"] + groups
    }
    
    var filteredChannels: [Channel] {
        var list = channels
        if selectedCategory == "Favoritos" {
            list = list.filter { $0.isFavorite }
        } else if selectedCategory != "Todos" {
            list = list.filter { $0.group == selectedCategory }
        }
        
        if !searchQuery.isEmpty {
            list = list.filter {
                $0.name.localizedCaseInsensitiveContains(searchQuery) ||
                $0.group.localizedCaseInsensitiveContains(searchQuery)
            }
        }
        return list
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Top Video Player Section (when channel selected)
                if let channel = selectedChannel, let url = URL(string: channel.streamUrl) {
                    VStack(alignment: .leading, spacing: 4) {
                        PlayerView(url: url)
                            .frame(height: 220)
                            .cornerRadius(12)
                            .padding(.horizontal)
                        
                        HStack {
                            Text(channel.name)
                                .font(.headline)
                                .foregroundColor(.white)
                            Spacer()
                            Button(action = { selectedChannel = nil }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray)
                            }
                        }
                        .padding(.horizontal)
                    }
                    .padding(.vertical, 8)
                }
                
                // Categories Bar
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories, id: \.self) { cat in
                            Button(action = { selectedCategory = cat }) {
                                Text(cat)
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(selectedCategory == cat ? Color.cyan : Color(white: 0.15))
                                    .foregroundColor(selectedCategory == cat ? .black : .white)
                                    .cornerRadius(16)
                            }
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical, 8)
                
                // Channel List
                if isLoading {
                    Spacer()
                    ProgressView("Carregando canais...")
                        .tint(.cyan)
                    Spacer()
                } else if filteredChannels.isEmpty {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "tv.slash")
                            .font(.system(size: 48))
                            .foregroundColor(.gray)
                        Text("Nenhum canal encontrado")
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                } else {
                    List(filteredChannels) { channel in
                        HStack(spacing: 12) {
                            if let logo = channel.logoUrl, let logoUrl = URL(string: logo) {
                                AsyncImage(url: logoUrl) { image in
                                    image.resizable().aspectRatio(contentMode: .fit)
                                } placeholder: {
                                    Image(systemName: "tv")
                                }
                                .frame(width: 40, height: 40)
                                .cornerRadius(6)
                            } else {
                                Image(systemName: "tv")
                                    .frame(width: 40, height: 40)
                                    .foregroundColor(.cyan)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(channel.name)
                                    .font(.body)
                                    .fontWeight(.semibold)
                                Text(channel.group)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            
                            Spacer()
                            
                            Button(action = { toggleFavorite(channel) }) {
                                Image(systemName: channel.isFavorite ? "star.fill" : "star")
                                    .foregroundColor(channel.isFavorite ? .yellow : .gray)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            selectedChannel = channel
                        }
                        .listRowBackground(Color(white: 0.1))
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Firehair IPTV")
            .searchable(text: $searchQuery, prompt: "Pesquisar canais...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action = { isShowingSettings = true }) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.cyan)
                    }
                }
            }
            .sheet(isPresented: $isShowingSettings) {
                PlaylistSetupView(
                    onLoadM3u: { url in loadM3u(url: url) },
                    onLoadXtream: { server, user, pass in loadXtream(server: server, user: user, pass: pass) }
                )
            }
        }
    }
    
    private func toggleFavorite(_ channel: Channel) {
        if let index = channels.firstIndex(where: { $0.id == channel.id }) {
            channels[index].isFavorite.toggle()
        }
    }
    
    private func loadM3u(url: String) {
        isLoading = true
        Task {
            guard let fetchUrl = URL(string: url), let (data, _) = try? await URLSession.shared.data(from: fetchUrl),
                  let content = String(data: data, encoding: .utf8) else {
                isLoading = false
                return
            }
            let parsed = M3uParser.parse(content: content)
            await MainActor.run {
                self.channels = parsed
                self.isLoading = false
                self.isShowingSettings = false
            }
        }
    }
    
    private func loadXtream(server: String, user: String, pass: String) {
        isLoading = true
        Task {
            let service = XtreamService()
            if let (info, fetchedChannels) = try? await service.fetchChannels(serverUrl: server, username: user, password: pass) {
                await MainActor.run {
                    self.channels = fetchedChannels
                    self.playlistInfo = PlaylistInfo(url: server, channelCount: fetchedChannels.size, connectionType: .xtreamCodes, xtreamAccountInfo: info)
                    self.isLoading = false
                    self.isShowingSettings = false
                }
            } else {
                await MainActor.run { self.isLoading = false }
            }
        }
    }
}
