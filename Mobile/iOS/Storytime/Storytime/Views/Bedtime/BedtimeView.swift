import SwiftUI

struct BedtimeView: View {
    @Binding var isPresented: Bool
    @ObservedObject private var audioManager = AmbientSoundManager.shared

    @AppStorage("bedtimeSound") private var selectedSoundKey = "whiteNoise"

    @State private var showHint = true
    @State private var showBreathingGuide = false
    @State private var previousBrightness: CGFloat = 0.5
    @State private var hasDimmed = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Background visual
            if showBreathingGuide {
                BreathingGuideView()
            } else {
                StarfieldView()
            }

            VStack(spacing: 32) {
                Spacer()

                // Clock display
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    Text(timeString(from: context.date))
                        .font(.system(size: 72, weight: .thin, design: .monospaced))
                        .foregroundStyle(Color(white: 0.6))
                }

                // Sound name + play/pause
                HStack(spacing: 16) {
                    Text(audioManager.currentSound.displayName)
                        .font(.title3)
                        .foregroundStyle(Color(white: 0.5))

                    Button {
                        if audioManager.isPlaying {
                            audioManager.stop()
                        } else {
                            let sound = AmbientSound(rawValue: selectedSoundKey) ?? .whiteNoise
                            audioManager.play(sound)
                        }
                    } label: {
                        Image(systemName: audioManager.isPlaying
                              ? "pause.circle.fill" : "play.circle.fill")
                            .font(.title)
                            .foregroundStyle(Color(white: 0.5))
                    }
                }

                // Volume slider
                HStack(spacing: 12) {
                    Image(systemName: "speaker.fill")
                        .foregroundStyle(Color(white: 0.3))
                        .font(.caption)

                    Slider(
                        value: Binding(
                            get: { Double(audioManager.volume) },
                            set: { audioManager.setVolume(Float($0)) }
                        ),
                        in: 0...1
                    )
                    .tint(Color(white: 0.4))

                    Image(systemName: "speaker.wave.3.fill")
                        .foregroundStyle(Color(white: 0.3))
                        .font(.caption)
                }
                .padding(.horizontal, 40)

                // Sound picker chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(AmbientSound.allCases) { sound in
                            Button {
                                selectedSoundKey = sound.rawValue
                                audioManager.play(sound, fadeDuration: 1.0)
                            } label: {
                                Label(sound.displayName, systemImage: sound.iconName)
                                    .font(.caption)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(
                                        audioManager.currentSound == sound
                                        ? Color.white.opacity(0.15)
                                        : Color.white.opacity(0.05)
                                    )
                                    .clipShape(Capsule())
                                    .foregroundStyle(Color(white: 0.6))
                            }
                        }
                    }
                    .padding(.horizontal)
                }

                Spacer()

                // Visual mode toggle
                Button {
                    withAnimation(.easeInOut(duration: 0.5)) {
                        showBreathingGuide.toggle()
                    }
                } label: {
                    Image(systemName: showBreathingGuide ? "sparkles" : "lungs")
                        .font(.title3)
                        .foregroundStyle(Color(white: 0.3))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                // Hint
                if showHint {
                    Text("Tap anywhere to exit")
                        .font(.caption)
                        .foregroundStyle(Color(white: 0.3))
                        .transition(.opacity)
                }
            }
            .padding()
        }
        .contentShape(Rectangle())
        .onTapGesture { dismiss() }
        .onAppear { enter() }
        .onDisappear { exit() }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
    }

    // MARK: - Clock Formatting

    private func timeString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm"
        return formatter.string(from: date)
    }

    // MARK: - Lifecycle

    private func enter() {
        // Save current brightness
        previousBrightness = UIScreen.main.brightness

        // Gradually dim screen
        dimScreen()

        // Start ambient sound with fade-in
        let sound = AmbientSound(rawValue: selectedSoundKey) ?? .whiteNoise
        audioManager.play(sound, fadeDuration: 3.0)

        // Hide hint after 3 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            withAnimation(.easeOut(duration: 1.0)) {
                showHint = false
            }
        }
    }

    private func dismiss() {
        exit()
        isPresented = false
    }

    private func exit() {
        audioManager.stop(fadeDuration: 1.0)
        restoreBrightness()
    }

    // MARK: - Brightness

    private func dimScreen() {
        guard !hasDimmed else { return }
        hasDimmed = true
        let steps = 30
        let interval = 2.0 / Double(steps)
        let startBrightness = UIScreen.main.brightness
        let targetBrightness: CGFloat = 0.05
        let delta = (targetBrightness - startBrightness) / CGFloat(steps)

        for i in 1...steps {
            DispatchQueue.main.asyncAfter(deadline: .now() + interval * Double(i)) {
                UIScreen.main.brightness = startBrightness + delta * CGFloat(i)
            }
        }
    }

    private func restoreBrightness() {
        let steps = 15
        let interval = 0.5 / Double(steps)
        let startBrightness = UIScreen.main.brightness
        let delta = (previousBrightness - startBrightness) / CGFloat(steps)

        for i in 1...steps {
            DispatchQueue.main.asyncAfter(deadline: .now() + interval * Double(i)) {
                UIScreen.main.brightness = startBrightness + delta * CGFloat(i)
            }
        }
    }
}
