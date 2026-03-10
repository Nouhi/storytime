import AVFoundation
import Combine

// MARK: - AmbientSound

enum AmbientSound: String, CaseIterable, Identifiable {
    case whiteNoise
    case brownNoise
    case pinkNoise
    case softRain

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .whiteNoise: return "White Noise"
        case .brownNoise: return "Brown Noise"
        case .pinkNoise: return "Pink Noise"
        case .softRain: return "Soft Rain"
        }
    }

    var iconName: String {
        switch self {
        case .whiteNoise: return "waveform"
        case .brownNoise: return "water.waves"
        case .pinkNoise: return "wind"
        case .softRain: return "cloud.rain"
        }
    }
}

// MARK: - AmbientSoundManager

@MainActor
class AmbientSoundManager: ObservableObject {
    static let shared = AmbientSoundManager()

    @Published var isPlaying = false
    @Published var currentSound: AmbientSound = .whiteNoise
    @Published var volume: Float = 0.5

    private var audioEngine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private var fadeTimer: Timer?

    // State for noise generation algorithms
    private var brownNoiseLastSample: Float = 0
    private var pinkNoiseB: [Float] = [0, 0, 0, 0, 0, 0, 0]
    private var rainFilterState: Float = 0

    private init() {}

    // MARK: - Public API

    func play(_ sound: AmbientSound, fadeDuration: TimeInterval = 3.0) {
        stop(fadeDuration: 0)
        currentSound = sound
        resetNoiseState()
        configureAudioSession()

        let engine = AVAudioEngine()
        let mainMixer = engine.mainMixerNode
        let outputFormat = mainMixer.outputFormat(forBus: 0)
        let sampleRate = outputFormat.sampleRate
        let channelCount = outputFormat.channelCount

        let generator = createGenerator(for: sound)

        let source = AVAudioSourceNode(format: outputFormat) { _, _, frameCount, audioBufferList -> OSStatus in
            let bufferList = UnsafeMutableAudioBufferListPointer(audioBufferList)
            for frame in 0..<Int(frameCount) {
                let sample = generator(sampleRate)
                for channel in 0..<Int(channelCount) {
                    let buffer = bufferList[channel]
                    let ptr = buffer.mData?.assumingMemoryBound(to: Float.self)
                    ptr?[frame] = sample
                }
            }
            return noErr
        }

        engine.attach(source)
        engine.connect(source, to: mainMixer, format: outputFormat)

        mainMixer.outputVolume = 0

        do {
            try engine.start()
            audioEngine = engine
            sourceNode = source
            isPlaying = true
            fadeVolume(to: volume, duration: fadeDuration)
        } catch {
            // Audio engine failed to start
        }
    }

    func stop(fadeDuration: TimeInterval = 1.0) {
        guard isPlaying else { return }
        fadeVolume(to: 0, duration: fadeDuration) { [weak self] in
            self?.audioEngine?.stop()
            if let source = self?.sourceNode {
                self?.audioEngine?.detach(source)
            }
            self?.audioEngine = nil
            self?.sourceNode = nil
            self?.isPlaying = false
        }
    }

    func setVolume(_ newVolume: Float) {
        volume = newVolume
        audioEngine?.mainMixerNode.outputVolume = newVolume
    }

    // MARK: - Audio Session

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true)
        } catch {
            // Audio session configuration failed
        }
    }

    // MARK: - Noise Generators

    private func resetNoiseState() {
        brownNoiseLastSample = 0
        pinkNoiseB = [0, 0, 0, 0, 0, 0, 0]
        rainFilterState = 0
    }

    private func createGenerator(for sound: AmbientSound) -> (Double) -> Float {
        switch sound {
        case .whiteNoise:
            return { [weak self] _ in
                guard self != nil else { return 0 }
                return Float.random(in: -1...1) * 0.3
            }

        case .brownNoise:
            return { [weak self] _ in
                guard let self = self else { return 0 }
                let white = Float.random(in: -1...1)
                self.brownNoiseLastSample = (self.brownNoiseLastSample + (0.02 * white)) / 1.02
                return self.brownNoiseLastSample * 3.5 * 0.3
            }

        case .pinkNoise:
            return { [weak self] _ in
                guard let self = self else { return 0 }
                let white = Float.random(in: -1...1)
                // Voss-McCartney pink noise approximation
                self.pinkNoiseB[0] = 0.99886 * self.pinkNoiseB[0] + white * 0.0555179
                self.pinkNoiseB[1] = 0.99332 * self.pinkNoiseB[1] + white * 0.0750759
                self.pinkNoiseB[2] = 0.96900 * self.pinkNoiseB[2] + white * 0.1538520
                self.pinkNoiseB[3] = 0.86650 * self.pinkNoiseB[3] + white * 0.3104856
                self.pinkNoiseB[4] = 0.55000 * self.pinkNoiseB[4] + white * 0.5329522
                self.pinkNoiseB[5] = -0.7616 * self.pinkNoiseB[5] - white * 0.0168980
                let pink = self.pinkNoiseB[0] + self.pinkNoiseB[1] + self.pinkNoiseB[2]
                    + self.pinkNoiseB[3] + self.pinkNoiseB[4] + self.pinkNoiseB[5]
                    + self.pinkNoiseB[6] + white * 0.5362
                self.pinkNoiseB[6] = white * 0.115926
                return pink * 0.11 * 0.3
            }

        case .softRain:
            return { [weak self] sampleRate in
                guard let self = self else { return 0 }
                let white = Float.random(in: -1...1)
                // Low-pass filter for a softer, rain-like sound
                let cutoff: Float = 800.0 / Float(sampleRate)
                let rc = 1.0 / (cutoff * 2.0 * .pi)
                let dt: Float = 1.0 / Float(sampleRate)
                let alpha = dt / (rc + dt)
                self.rainFilterState = self.rainFilterState + alpha * (white - self.rainFilterState)
                // Add occasional louder "drops"
                let drop: Float = Float.random(in: 0...1) < 0.001 ? Float.random(in: 0.3...0.6) : 0
                return (self.rainFilterState * 1.5 + drop) * 0.3
            }
        }
    }

    // MARK: - Volume Fade

    private func fadeVolume(
        to target: Float,
        duration: TimeInterval,
        completion: (() -> Void)? = nil
    ) {
        fadeTimer?.invalidate()

        guard duration > 0 else {
            audioEngine?.mainMixerNode.outputVolume = target
            completion?()
            return
        }

        let steps = 30
        let interval = duration / Double(steps)
        let startVolume = audioEngine?.mainMixerNode.outputVolume ?? 0
        let delta = (target - startVolume) / Float(steps)
        var step = 0

        fadeTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] timer in
            step += 1
            let newVol = startVolume + delta * Float(step)
            self?.audioEngine?.mainMixerNode.outputVolume = newVol
            if step >= steps {
                timer.invalidate()
                self?.audioEngine?.mainMixerNode.outputVolume = target
                completion?()
            }
        }
    }
}
